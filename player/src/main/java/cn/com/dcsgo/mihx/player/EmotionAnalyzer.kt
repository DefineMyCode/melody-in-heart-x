package cn.com.dcsgo.mihx.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.domain.repository.EmotionAnalysisResult
import cn.com.dcsgo.mihx.domain.repository.EmotionFailureReason
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter // LiteRT(坐标已更名)保留原 Java 包名, 见官方迁移指南
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 整曲情绪分析器: 任意音频 Uri -> 逐窗 (valence, arousal) 曲线 + 全曲均值.
 *
 * 管线(与服务器训练/评测及 tf-examples 探针逐位一致, 探针实测 maxDiff=6e-6):
 *   MediaCodec 解码 -> 16k mono Float PCM -> 5s 窗/2.5s hop 滑窗
 *   -> 窗内 16384 样本帧 YAMNet embedding 均值 -> va_head -> (V,A)
 *
 * 曲线存原始逐窗值, 平滑由渲染端负责.
 * 模型文件: assets/emotion_yamnet.tflite + emotion_va_head.tflite.
 *
 * 内存纪律(真机 OOM 教训 2026-08-30): 解码 PCM 一律放原始类型数组.
 * 曾用 ArrayList<Short>(初始容量 srcSr*200) 逐样本装箱: 4min@44.1k 立体声
 * = 1058 万个 Short 对象(≈16B/个) + 70MB 引用数组 ≈ 240MB, 叠加推理输出
 * 数组顶穿 256MB 堆上限, GC 回收 0 后连 ExoPlayer 线程分配 2.5KB 都 OOM 崩进程.
 */
@Singleton
class EmotionAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ffmpegDecoder: FfmpegPcmDecoder,
) {
    private val lock = Any()

    /** 解码内存预算（自适应设备堆上限，2026-10-21）：读取一次即可，堆上限运行期不变 */
    private val memoryBudgetBytes: Long = memoryBudgetForHeap(Runtime.getRuntime().maxMemory())

    private var yam: Interpreter
    private var head: Interpreter
    private val embIdx: Int

    init {
        // 与探针同款: 动态定位 embedding 输出(1024 维), 不硬编码索引
        yam = Interpreter(modelBuf(YAMNET_ASSET))
        embIdx = (0 until yam.outputTensorCount)
            .first { yam.getOutputTensor(it).shape().last() == 1024 }
        head = Interpreter(modelBuf(HEAD_ASSET))
    }

    private fun modelBuf(name: String): ByteBuffer =
        context.assets.open(name).readBytes()
            .let { b ->
                ByteBuffer.allocateDirect(b.size)
                    .order(ByteOrder.nativeOrder())
                    .apply { put(b); rewind() }
            }

    /**
     * 分析整曲. 耗时 CPU 密集(实测 ~120ms/窗), 调用方须放后台线程.
     * @return Failure 携带可展示的失败原因(内部已记日志), 由调用方持久化
     */
    fun analyze(
        songId: Int,
        uri: Uri,
        modelVersion: String,
        onProgress: (Float) -> Unit = {},
    ): EmotionAnalysisResult {
        return try {
            val pcm = decodeToPcm16kMono(uri)
            if (pcm.first == null) {
                // M-7：解码失败原因在 second；first 非空时 second 必为 null（解码契约）
                val reason = pcm.second ?: EmotionFailureReason.EXTRACT_FAILED
                return EmotionAnalysisResult.Failure(reason)
            }
            val pcmData = pcm.first ?: return EmotionAnalysisResult.Failure(EmotionFailureReason.EXTRACT_FAILED)
            if (pcmData.size < WINDOW_SAMPLES) {
                AppLog.warning(TAG, "analyze skip: too short songId=$songId", null)
                return EmotionAnalysisResult.Failure(EmotionFailureReason.TOO_SHORT)
            }
            val series = ArrayList<Pair<Float, Float>>()
            val inBuf = ByteBuffer.allocateDirect(FRAME * 4).order(ByteOrder.nativeOrder())
            val headIn = ByteBuffer.allocateDirect(1024 * 4).order(ByteOrder.nativeOrder())
            val headOut = Array(1) { FloatArray(2) }
            // 复用输出数组(整曲一份): 原逐帧 new embArr/scoreArr/map, 一首歌多分配
            // ~6MB 短命对象, 堆紧时放大 GC 停顿
            val embArr = Array(2) { FloatArray(1024) }
            val scoreArr = Array(2) { FloatArray(521) }
            val otherIdx = if (embIdx == 0) 1 else 0
            val yamOut = mapOf(embIdx to embArr, otherIdx to scoreArr)
            val songEmb = DoubleArray(1024) // 整曲 embedding 累加器(kNN 锚点用)
            // M-7（评审 2026-10-15）：进度逐窗上报。原实现在整个推理循环结束后只调一次，
            // 进度条 0→100 一跳等于没有。回调在持锁的推理线程内执行，调用方必须保持轻量
            // （仅写进度状态）；批扫 Worker 的现有回调满足此约定。
            val totalWindows = (pcmData.size - WINDOW_SAMPLES) / HOP_SAMPLES + 1
            var s = 0
            var windowDone = 0
            // 解码在锁外(纯 CPU, 无共享状态); 推理全程持锁: 周期/手动是两个不同
            // unique 任务, WorkManager 可并发跑两个 Worker, TFLite Interpreter
            // 非线程安全, 并发 invoke 会 native 崩溃 — 与 close() 同一把 lock 串行化.
            synchronized(lock) {
                while (s + WINDOW_SAMPLES <= pcmData.size) {
                    val acc = FloatArray(1024)
                    var nf = 0
                    var st = s
                    while (st + FRAME <= s + WINDOW_SAMPLES) {
                        inBuf.rewind()
                        inBuf.asFloatBuffer().put(pcmData, st, FRAME)
                        inBuf.rewind()
                        yam.runForMultipleInputsOutputs(arrayOf(inBuf), yamOut)
                        for (fr in embArr) for (j in 0 until 1024) acc[j] += fr[j]
                        nf += 2
                        st += FRAME
                    }
                    check(nf > 0)
                    for (j in 0 until 1024) acc[j] /= nf
                    for (j in 0 until 1024) songEmb[j] += acc[j]
                    headIn.rewind()
                    headIn.asFloatBuffer().put(acc)
                    headIn.rewind()
                    head.run(headIn, headOut)
                    // A（2026-10-24）：arousal 改为**真实响度包络**（物理定义），
                    //    不再取 VA 头的 arousal 输出——后者是"曲风代理"：
                    //    YAMNet 认得鼓/电子/嘻哈，测的是"像不像那类曲风"，
                    //    导致「燃」「静谧」这类**绝对能量**词条几乎不可达。
                    //    窗内 RMS 直接从已解码的 16k PCM 算，零额外解码、零额外分配。
                    val arousal = windowArousal(pcmData, s)
                    // B（2026-10-24）：valence 零点/斜率标定——模型输出系统性正偏
                    //    （真机 1094 首歌级均值 +0.264），标定后居中（+0.017）。
                    val valence = calibrateValence(headOut[0][0])
                    series.add(valence to arousal)
                    windowDone++
                    onProgress((windowDone.toFloat() / totalWindows).coerceIn(0f, 1f))
                    s += HOP_SAMPLES
                }
            }
            var peak = 0
            // 高潮 = A(能量)正向峰值
            for (i in series.indices) if (series[i].second > series[peak].second) peak = i
            val nWin = series.size.coerceAtLeast(1)
            EmotionAnalysisResult.Success(
                SongEmotion(
                    songId = songId,
                    valence = series.map { it.first }.average().toFloat(),
                    arousal = series.map { it.second }.average().toFloat(),
                    curve = series,
                    peakSec = peak * HOP_SEC,
                    windowsAnalyzed = series.size,
                    durationSec = pcmData.size / SR.toFloat(),
                    modelVersion = modelVersion,
                    analyzedAt = System.currentTimeMillis(),
                    embedding = FloatArray(1024) { j -> (songEmb[j] / nWin).toFloat() },
                ),
            )
        } catch (oom: OutOfMemoryError) {
            // OOM 是 Error 不走 Exception 分支（2026-09-04 真机日志: 超长合集解码
            // ShortAccum 扩容 245MB 失败, 曾被 Worker 兜底误标为 INFERENCE_ERROR）。
            // 解码前已按时长/内存预算熔断, 走到这里通常是时长未知(durationUs<=0)的极端文件。
            // 2026-10-21: 高采样率(192kHz)/大文件解码内存耗尽也是 OOM, 归为「内存超限」而非「超20分钟」。
            AppLog.warning(TAG, "analyze OOM songId=$songId: ${oom.message}", oom)
            EmotionAnalysisResult.Failure(EmotionFailureReason.TOO_LARGE_FOR_DEVICE)
        } catch (e: Exception) {
            AppLog.warning(TAG, "analyze failed songId=$songId: ${e.message}", e)
            EmotionAnalysisResult.Failure(EmotionFailureReason.INFERENCE_ERROR)
        }
    }

    /**
     * MediaExtractor + MediaCodec 解码为 16k mono Float PCM.
     * 返回 (PCM, 失败原因)；失败原因非 null 时 PCM 为 null（2026-09-04 失败标记）。
     */
    private fun decodeToPcm16kMono(uri: Uri): Pair<FloatArray?, EmotionFailureReason?> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            AppLog.warning(TAG, "extractor open failed: ${e.message}", e)
            extractor.release()
            return null to EmotionFailureReason.EXTRACT_FAILED
        }
        var track = -1
        var format: MediaFormat? = null
        var rawTrack = -1
        var rawFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("audio/")) continue
            if (mime == "audio/raw") {
                if (rawTrack < 0) { rawTrack = i; rawFormat = f }
            } else if (track < 0) {
                track = i; format = f
            }
        }
        // 部分容器把真音频嗅成 raw(封面轨坑), 无更优轨时兜底用 raw
        if (track < 0 && rawTrack >= 0) { track = rawTrack; format = rawFormat }
        if (track < 0) {
            AppLog.warning(TAG, "no audio track uri=$uri", null)
            extractor.release()
            return null to EmotionFailureReason.NO_AUDIO_TRACK
        }
        extractor.selectTrack(track)
        val mime = format!!.getString(MediaFormat.KEY_MIME)!!
        val srcSr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcCh = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val deadline = System.currentTimeMillis() + MAX_DECODE_MS

        // 超长/内存预算熔断前置（2026-09-04 超长；2026-10-21 高采样率/大文件）：解码前就能拿到
        // 轨道时长。按「时长×源采样率」折算预计样本数，先命中 20 分钟上限 → 超长；仅命中内存
        // 预算 → 采样率过高/文件过大（192kHz 下 20 分钟上限形同虚设，内存预算先到）。
        // durationUs <= 0(未知)时不拦，交给解码中截断 + OOM 兜底。
        val durationUs = try {
            format.getLong(MediaFormat.KEY_DURATION) // key = "durationUs"
        } catch (e: Exception) {
            0L
        }
        if (durationUs > 0L) {
            val cutoffReason = samplesCutoffReason(srcSr, durationUs * srcSr / 1_000_000L, memoryBudgetBytes)
            if (cutoffReason != null) {
                val (byDuration, byMemory) = samplesLimits(srcSr, memoryBudgetBytes)
                AppLog.warning(
                    TAG,
                    "song rejected before decode: duration=${durationUs / 1_000_000}s @${srcSr}Hz, " +
                        "estSamples=${durationUs * srcSr / 1_000_000L}, " +
                        "durationLimit=${byDuration}, memoryLimit=${byMemory}, " +
                        "budget=${memoryBudgetBytes / (1024 * 1024)}MB -> ${cutoffReason.name}",
                    null,
                )
                extractor.release()
                return null to cutoffReason
            }
        }

        // FFmpeg 优先(与播放链路同款解码器): 厂商软解组件在部分 ROM 上 native 崩溃/死循环,
        // 见 FfmpegPcmDecoder KDoc. 危险 mime(仅厂商软解可用)失败后禁止回退 MediaCodec.
        if (FfmpegPcmDecoder.handles(mime)) {
            val res = ffmpegDecoder.decode(extractor, format, deadline)
            if (res != null) {
                try {
                    return resampleTo16k(res.pcm, res.sampleRate) to null
                } finally {
                    extractor.release()
                }
            }
            if (!FfmpegPcmDecoder.mediaCodecFallbackSafe(mime)) {
                AppLog.warning(TAG, "decode gave up (ffmpeg-only mime=$mime failed)", null)
                extractor.release()
                return null to EmotionFailureReason.DECODE_UNSUPPORTED_FORMAT
            }
            // 回退厂商解码前 rewind: FFmpeg 路径已消费过部分样本
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Exception) {
            // raw 轨无对应解码器: 直接按 PCM s16 读
            if (mime == "audio/raw") {
                return try {
                    // readRawPcm 内部已完成重采样，外层不再二次重采样
                    val raw = readRawPcm(extractor, srcSr, srcCh, deadline)
                    if (raw.first != null) {
                        raw.first to null
                    } else {
                        null to (raw.second ?: EmotionFailureReason.DECODE_UNSUPPORTED_FORMAT)
                    }
                } finally {
                    extractor.release()
                }
            }
            AppLog.warning(TAG, "no decoder for $mime: ${e.message}", e)
            extractor.release()
            return null to EmotionFailureReason.DECODE_UNSUPPORTED_FORMAT
        }
        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = ShortAccum(maxCapacity = maxSamplesForSrc(srcSr, memoryBudgetBytes).toInt())
            val info = MediaCodec.BufferInfo()
            var eosIn = false
            var eosOut = false
            while (!eosOut) {
                if (System.currentTimeMillis() > deadline) {
                    AppLog.warning(TAG, "decode timeout after ${MAX_DECODE_MS}ms, abort", null)
                    return null to EmotionFailureReason.DECODE_TIMEOUT
                }
                if (!eosIn) {
                    val inIdx = codec.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val sampleSize = extractor.readSampleData(codec.getInputBuffer(inIdx)!!, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            eosIn = true
                        } else {
                            codec.queueInputBuffer(
                                inIdx, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10000)
                if (outIdx >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosOut = true
                    val bb = codec.getOutputBuffer(outIdx)!!
                    bb.position(info.offset)
                    bb.limit(info.offset + info.size)
                    bb.order(ByteOrder.LITTLE_ENDIAN)
                    val total = info.size / 2 / srcCh
                    for (k in 0 until total) {
                        if (srcCh == 1) {
                            pcm.add(bb.short)
                        } else {
                            var sum = 0
                            for (c in 0 until srcCh) sum += bb.short
                            pcm.add((sum / srcCh).toShort())
                        }
                        // 解码中截断熔断：ShortAccum 触顶即静默丢弃，据 truncated 判定是
                        // 「超长」还是「内存超限」（高采样率/大文件）——2026-10-21 区分原因
                        if (pcm.truncated) {
                            val reason = truncationReason(srcSr, memoryBudgetBytes)
                            AppLog.warning(
                                TAG,
                                "decode truncated @${srcSr}Hz (${pcm.size} samples) -> ${reason.name}",
                                null,
                            )
                            return null to reason
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }
            codec.stop()
            resampleTo16k(pcm, srcSr) to null
        } catch (e: Exception) {
            AppLog.warning(TAG, "decode failed: ${e.message}", e)
            null to EmotionFailureReason.DECODE_UNSUPPORTED_FORMAT
        } finally {
            codec.release()
            extractor.release()
        }
    }

    /** audio/raw 兜底轨: extractor 直读 PCM s16. */
    private fun readRawPcm(
        extractor: MediaExtractor,
        srcSr: Int,
        srcCh: Int,
        deadline: Long,
    ): Pair<FloatArray?, EmotionFailureReason?> {
        return try {
            val pcm = ShortAccum(maxCapacity = maxSamplesForSrc(srcSr, memoryBudgetBytes).toInt())
            val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    AppLog.warning(TAG, "raw read timeout, abort", null)
                    return null to EmotionFailureReason.DECODE_TIMEOUT
                }
                buf.clear()
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                buf.limit(size)
                val total = size / 2 / srcCh
                for (k in 0 until total) {
                    if (srcCh == 1) {
                        pcm.add(buf.short)
                    } else {
                        var sum = 0
                        for (c in 0 until srcCh) sum += buf.short
                        pcm.add((sum / srcCh).toShort())
                    }
                    if (pcm.truncated) {
                        val reason = truncationReason(srcSr, memoryBudgetBytes)
                        AppLog.warning(TAG, "raw decode truncated @${srcSr}Hz -> ${reason.name}", null)
                        return null to reason
                    }
                }
                buf.rewind()
                extractor.advance()
            }
            resampleTo16k(pcm, srcSr) to null
        } catch (e: Exception) {
            AppLog.warning(TAG, "raw pcm failed: ${e.message}", e)
            null to EmotionFailureReason.DECODE_UNSUPPORTED_FORMAT
        }
    }

    /** 线性重采样到 16k; Long 防溢出(4min@44.1k 样本数×16000 超 Int). */
    private fun resampleTo16k(pcm: ShortAccum, srcSr: Int): FloatArray {
        val inN = pcm.size
        val out = FloatArray((inN.toLong() * SR / srcSr).toInt())
        val ratio = srcSr.toFloat() / SR
        for (i in out.indices) {
            val pos = i * ratio
            val lo = pos.toInt().coerceAtMost(inN - 1)
            val hi = (lo + 1).coerceAtMost(inN - 1)
            val frac = pos - lo
            out[i] = (pcm.data[lo] * (1 - frac) + pcm.data[hi] * frac) / 32768f
        }
        return out
    }

    /** 释放 native 资源(库清空/退出时可选调用). */
    fun close() = synchronized(lock) {
        runCatching { yam.close() }
        runCatching { head.close() }
    }

    companion object {
        private const val TAG = "EmotionAnalyzer"
        private const val YAMNET_ASSET = "emotion_yamnet.tflite"
        private const val HEAD_ASSET = "emotion_va_head.tflite"

        /**
         * 模型版本戳：换模型/换语义后据此判定重扫。
         *
         * v4（2026-10-24）：arousal 改真实响度包络 + valence 零点斜率标定。
         * 见 docs/architecture/EMOTION_VA_UPGRADE.md。
         *
         * 取值引用 [cn.com.dcsgo.mihx.core.model.EmotionGroup.CURRENT_MODEL_VERSION]
         * —— 锚点表要按这个版本号判断曲线属于哪套坐标系, 两处不能各写一份。
         */
        const val MODEL_VERSION = cn.com.dcsgo.mihx.core.model.EmotionGroup.CURRENT_MODEL_VERSION
        const val SR = 16000
        const val FRAME = 16384
        const val WINDOW_SEC = 5.0f
        const val HOP_SEC = 2.5f
        /** 滑窗窗长（样本数）：5s @16k */
        const val WINDOW_SAMPLES = (WINDOW_SEC * SR).toInt()
        private const val HOP_SAMPLES = (HOP_SEC * SR).toInt()

        /**
         * arousal 映射区间（dBFS，方案 A）。
         *
         * 固定物理标定，**不随曲库变化**——若用库内自适应区间，同一首歌在两台
         * 曲库不同的设备上会得到不同词条，用户会认为功能有 bug。
         *
         * 语义自洽（由区间反推各词条的典型响度）：燃 ≈ −6.9dB / 治愈 ≈ −18.8dB /
         * 静谧 ≈ −24.4dB —— 安静抒情、很响的燃、中间态的治愈，量级都对得上。
         *
         * 依据：真机 69 首样本的窗内 dB 分布 median −11.8 / p5 −28.1 / p95 −5.2。
         * 更深的下限（−60dB）会让低能量段不可达（实测 0.7%），「静谧」「窒息」
         * 这类词条永远出不来。
         */
        const val DB_FLOOR = -30.0
        const val DB_CEIL = -5.0

        /**
         * valence 零点/斜率标定（方案 B）：v' = (v − B) / S。
         *
         * B 来自真机 1094 首的歌级 valence 均值 +0.264（标定后 +0.017，居中）；
         * S 保留原尺度的八成，避免过度放大噪声。
         */
        const val V_CAL_BIAS = 0.25f
        const val V_CAL_SCALE = 0.85f

        /** 窗内 RMS（复用已解码的 16k PCM，零额外解码、零额外分配） */
        fun windowRms(pcm: FloatArray, start: Int, length: Int): Double {
            var sumSq = 0.0
            var i = start
            val end = start + length
            while (i < end) {
                val x = pcm[i].toDouble()
                sumSq += x * x
                i++
            }
            return kotlin.math.sqrt(sumSq / length)
        }

        /** dBFS → arousal ∈ [-1,1]（区间外截断） */
        fun dbToArousal(db: Double): Float {
            val a = (db - DB_FLOOR) / (DB_CEIL - DB_FLOOR) * 2.0 - 1.0
            return a.coerceIn(-1.0, 1.0).toFloat()
        }

        /** 窗内响度 → arousal（方案 A 的完整一步） */
        fun windowArousal(pcm: FloatArray, start: Int = 0, length: Int = WINDOW_SAMPLES): Float {
            val rms = windowRms(pcm, start, length)
            return dbToArousal(20.0 * kotlin.math.log10(rms + 1e-6))
        }

        /** valence 零点/斜率标定（方案 B） */
        fun calibrateValence(v: Float): Float =
            ((v - V_CAL_BIAS) / V_CAL_SCALE).coerceIn(-1f, 1f)

        /** 单曲解码看门狗: 正常 <10s, 卡死时 60s 强制放弃该曲 */
        private const val MAX_DECODE_MS = 60_000L

        /** 超长曲熔断: 20 分钟。解码前置用 [MAX_DURATION_US]（轨道时长），事后熔断按实际采样率算样本数 */
        const val MAX_SECONDS = 20 * 60

        /** 解码前置熔断阈值（微秒）：轨道时长超过即跳过，不启动解码 */
        const val MAX_DURATION_US = MAX_SECONDS * 1_000_000L

        /**
         * 事后熔断样本数上限：按实际源采样率换算，且带解码内存预算。
         *
         * 两条约束取小：
         * 1. 时长上限：MAX_SECONDS × 源采样率（20 分钟语义）；
         * 2. 内存预算：m-7（评审 2026-10-15）——解码 ShortAccum(2B/样本) 与重采样输出
         *    (4B/16k样本) 同时存活，峰值 ≈ 样本数 × (2 + 64000/srcSr) 字节。
         *    旧实现只按样本数熔断：44.1kHz 下 20 分钟 ≈ 106MB 可扛，但 96kHz 的 11 分钟
         *    文件（230MB+ 扩容瞬时 1.5 倍）必然 OOM——靠 catch(OutOfMemoryError) 兜底不稳。
         *    预算 192MB 下：44.1k/48k 仍由 20 分钟上限支配（行为不变），
         *    96k → 7.5 分钟、192k → 3.3 分钟确定性熔断，不再 OOM。
         *
         * 采样率先 clamp 到合法编解码范围（8k–192k），避免畸形头报告的极端采样率
         * 让数值溢出 Int（ShortAccum.maxCapacity 是 Int）。
         */
        fun maxSamplesForSrc(srcSampleRate: Int, memoryBudgetBytes: Long): Long {
            val (byDuration, byMemory) = samplesLimits(srcSampleRate, memoryBudgetBytes)
            return minOf(byDuration, byMemory).coerceAtMost(Int.MAX_VALUE.toLong())
        }

        /**
         * 解码样本数双限（2026-10-21）：(时长上限, 内存上限)。
         * - 时长上限：20 分钟 × 源采样率（真正的「超长」）；
         * - 内存上限：解码缓冲 + 重采样输出的内存预算换算的样本数（高采样率/大文件先命中）。
         *
         * 内存上限再分两段（2026-10-21 OOM 回归）：
         * - 稳态峰值：ShortAccum(2B/样本) + 16k 重采样输出(4B/16k样本) = 样本数 × (2 + 64000/rate) 字节；
         * - 倍增瞬时峰值：ShortAccum 最后一次 copyOf 倍增时旧数组(样本数×1 字节) 与新数组
         *   (样本数×2 字节) 同时存活 = 样本数 × 3 字节。仅在 rate > 64kHz 时大于稳态（此时
         *   3 > 2 + 64000/rate），是 192kHz 歌曲解码中 OOM 的根因（真机 copyOf 分配 172MB OOM）。
         * 高采样率取两者较小者，并给瞬时限留安全余量（×2/7 ≈ /3.5，覆盖基线预留偏差）。
         */
        fun samplesLimits(srcSampleRate: Int, memoryBudgetBytes: Long): Pair<Long, Long> {
            val rate = srcSampleRate.coerceIn(MIN_SRC_SAMPLE_RATE, MAX_SRC_SAMPLE_RATE)
            val byDuration = MAX_SECONDS.toLong() * rate
            val steadyLimit = memoryBudgetBytes * rate / (2L * rate + BYTES_PER_RESAMPLED_SAMPLE * SR)
            val transientLimit = memoryBudgetBytes * 2 / 7 // ≈ budget/3.5，含基线预留偏差的安全余量
            val byMemory = if (rate > TRANSIENT_DOMINANT_RATE) {
                minOf(steadyLimit, transientLimit)
            } else {
                steadyLimit
            }
            return byDuration.coerceAtMost(Int.MAX_VALUE.toLong()) to
                byMemory.coerceAtMost(Int.MAX_VALUE.toLong())
        }

        /**
         * 由「预计解码样本数」判定熔断原因（2026-10-21）：
         * - 先超时长上限 → [EmotionFailureReason.TOO_LONG]（>20 分钟）；
         * - 仅超内存上限 → [EmotionFailureReason.TOO_LARGE_FOR_DEVICE]（采样率过高/文件过大，
         *   时长未超 20 分钟）。未超限返回 null。
         */
        fun samplesCutoffReason(
            srcSampleRate: Int,
            estimatedSamples: Long,
            memoryBudgetBytes: Long,
        ): EmotionFailureReason? {
            val (byDuration, byMemory) = samplesLimits(srcSampleRate, memoryBudgetBytes)
            return when {
                estimatedSamples > byDuration -> EmotionFailureReason.TOO_LONG
                estimatedSamples > byMemory -> EmotionFailureReason.TOO_LARGE_FOR_DEVICE
                else -> null
            }
        }

        /**
         * ShortAccum 解码中触顶截断后的原因判定（2026-10-21）：
         * 容量 = min(时长, 内存) 上限，触顶时 size 即该较小值——哪个上限更小就是哪个先命中。
         */
        fun truncationReason(srcSampleRate: Int, memoryBudgetBytes: Long): EmotionFailureReason {
            val (byDuration, byMemory) = samplesLimits(srcSampleRate, memoryBudgetBytes)
            return if (byDuration <= byMemory) {
                EmotionFailureReason.TOO_LONG
            } else {
                EmotionFailureReason.TOO_LARGE_FOR_DEVICE
            }
        }

        /**
         * 解码内存预算（字节，自适应，2026-10-21）：解码缓冲 + 重采样输出合计的软上限。
         * = 设备实际堆上限 - 基线预留（推理模型 + 应用基线，不随堆大小缩放）。
         * - 256MB 堆 → 192MB（与旧固定值一致，低端机行为不变）；
         * - 384MB 堆 → 320MB；512MB 堆 → 448MB——更大堆自动放宽，容纳更长的 192k 文件。
         * 上下限防极端堆值。
         */
        fun memoryBudgetForHeap(maxHeapBytes: Long): Long {
            return (maxHeapBytes - RESERVED_BYTES).coerceIn(MIN_BUDGET_BYTES, MAX_BUDGET_BYTES)
        }

        /** 基线预留（推理模型 + 应用基线，不随堆大小缩放） */
        private const val RESERVED_BYTES = 64L * 1024 * 1024

        /** 解码内存预算下限（过小的堆仍保留最小分析能力） */
        private const val MIN_BUDGET_BYTES = 96L * 1024 * 1024

        /** 解码内存预算上限（防极端堆值把预算抬到危险高度） */
        private const val MAX_BUDGET_BYTES = 512L * 1024 * 1024

        /** 重采样后每样本字节数（16k mono Float = 4B） */
        private const val BYTES_PER_RESAMPLED_SAMPLE = 4L

        /* 合法源采样率范围（clamp 用） */
        private const val MIN_SRC_SAMPLE_RATE = 8_000
        private const val MAX_SRC_SAMPLE_RATE = 192_000

        /**
         * 采样率超过该值时，ShortAccum 倍增瞬时峰值（3 字节/样本）超过稳态峰值
         * （2 + 64000/rate 字节/样本），内存上限需同时约束瞬时限（2026-10-21 OOM 回归）。
         */
        private const val TRANSIENT_DOMINANT_RATE = 64_000
    }
}
