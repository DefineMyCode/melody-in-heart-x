package cn.com.dcsgo.mihx.data.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.SongEmotion
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
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
     * @return null 表示解码/推理失败(内部已记日志)
     */
    fun analyze(
        songId: Int,
        uri: Uri,
        modelVersion: String,
        onProgress: (Float) -> Unit = {},
    ): SongEmotion? {
        return try {
            val pcm = decodeToPcm16kMono(uri) ?: return null
            if (pcm.size < WINDOW_SAMPLES) {
                AppLog.warning(TAG, "analyze skip: too short songId=$songId", null)
                return null
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
            var s = 0
            while (s + WINDOW_SAMPLES <= pcm.size) {
                val acc = FloatArray(1024)
                var nf = 0
                var st = s
                while (st + FRAME <= s + WINDOW_SAMPLES) {
                    inBuf.rewind()
                    inBuf.asFloatBuffer().put(pcm, st, FRAME)
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
                series.add(headOut[0][0] to headOut[0][1])
                s += HOP_SAMPLES
                onProgress(s.toFloat() / (pcm.size - WINDOW_SAMPLES).coerceAtLeast(1))
            }
            var peak = 0
            // 高潮 = A(能量)正向峰值
            for (i in series.indices) if (series[i].second > series[peak].second) peak = i
            val nWin = series.size.coerceAtLeast(1)
            SongEmotion(
                songId = songId,
                valence = series.map { it.first }.average().toFloat(),
                arousal = series.map { it.second }.average().toFloat(),
                curve = series,
                peakSec = peak * HOP_SEC,
                windowsAnalyzed = series.size,
                durationSec = pcm.size / SR.toFloat(),
                modelVersion = modelVersion,
                analyzedAt = System.currentTimeMillis(),
                embedding = FloatArray(1024) { j -> (songEmb[j] / nWin).toFloat() },
            )
        } catch (e: Exception) {
            AppLog.warning(TAG, "analyze failed songId=$songId: ${e.message}", e)
            null
        }
    }

    /** MediaExtractor + MediaCodec 解码为 16k mono Float PCM. */
    private fun decodeToPcm16kMono(uri: Uri): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            AppLog.warning(TAG, "extractor open failed: ${e.message}", e)
            return null
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
            return null
        }
        extractor.selectTrack(track)
        val mime = format!!.getString(MediaFormat.KEY_MIME)!!
        val srcSr = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val srcCh = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val deadline = System.currentTimeMillis() + MAX_DECODE_MS

        // FFmpeg 优先(与播放链路同款解码器): 厂商软解组件在部分 ROM 上 native 崩溃/死循环,
        // 见 FfmpegPcmDecoder KDoc. 危险 mime(仅厂商软解可用)失败后禁止回退 MediaCodec.
        if (FfmpegPcmDecoder.handles(mime)) {
            val res = ffmpegDecoder.decode(extractor, format, deadline)
            if (res != null) {
                try {
                    return resampleTo16k(res.pcm, res.sampleRate)
                } finally {
                    extractor.release()
                }
            }
            if (!FfmpegPcmDecoder.mediaCodecFallbackSafe(mime)) {
                AppLog.warning(TAG, "decode gave up (ffmpeg-only mime=$mime failed)", null)
                extractor.release()
                return null
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
                    readRawPcm(extractor, srcSr, srcCh, deadline)
                } finally {
                    extractor.release()
                }
            }
            AppLog.warning(TAG, "no decoder for $mime: ${e.message}", e)
            extractor.release()
            return null
        }
        return try {
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = ShortAccum()
            val info = MediaCodec.BufferInfo()
            var eosIn = false
            var eosOut = false
            while (!eosOut) {
                if (System.currentTimeMillis() > deadline) {
                    AppLog.warning(TAG, "decode timeout after ${MAX_DECODE_MS}ms, abort", null)
                    return null
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
                        if (pcm.size > MAX_SAMPLES) {
                            AppLog.warning(TAG, "song too long (>${MAX_SECONDS / 60}min), skip", null)
                            return null
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
            }
            codec.stop()
            resampleTo16k(pcm, srcSr)
        } catch (e: Exception) {
            AppLog.warning(TAG, "decode failed: ${e.message}", e)
            null
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
    ): FloatArray? {
        return try {
            val pcm = ShortAccum()
            val buf = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    AppLog.warning(TAG, "raw read timeout, abort", null)
                    return null
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
                    if (pcm.size > MAX_SAMPLES) {
                        AppLog.warning(TAG, "raw song too long, skip", null)
                        return null
                    }
                }
                buf.rewind()
                extractor.advance()
            }
            resampleTo16k(pcm, srcSr)
        } catch (e: Exception) {
            AppLog.warning(TAG, "raw pcm failed: ${e.message}", e)
            null
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
        const val MODEL_VERSION = "yamnet-va-v3"
        const val SR = 16000
        const val FRAME = 16384
        const val WINDOW_SEC = 5.0f
        const val HOP_SEC = 2.5f
        private const val WINDOW_SAMPLES = (WINDOW_SEC * SR).toInt()
        private const val HOP_SAMPLES = (HOP_SEC * SR).toInt()

        /** 单曲解码看门狗: 正常 <10s, 卡死时 60s 强制放弃该曲 */
        private const val MAX_DECODE_MS = 60_000L

        /** 超长曲熔断(源采样率侧样本数上限, 20min@192k 最坏 ≈46M 样本): 防解码异常回环烧内存 */
        const val MAX_SECONDS = 20 * 60
        const val MAX_SAMPLES = MAX_SECONDS * 192_000
    }
}
