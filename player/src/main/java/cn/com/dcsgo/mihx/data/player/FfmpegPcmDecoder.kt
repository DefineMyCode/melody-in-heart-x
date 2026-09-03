package cn.com.dcsgo.mihx.data.player

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import cn.com.dcsgo.mihx.core.common.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FFmpeg 软解桥: MediaExtractor(已 selectTrack) -> mono s16 PCM.
 *
 * 背景(真机 SIGSEGV 2026-08-31): [EmotionAnalyzer] 的 MediaCodec 同步解码循环在
 * 部分 ROM 的厂商软解组件上进程级崩溃(native 野指针, catch 不住):
 *  - 小米高通 [c2.qti.alac.sw.decoder](96k ALAC) configure/start 后立即自杀
 *    (组件报 UNKNOWN_ERROR -> 状态机掉到 Released), 调用线程下一拍 queueInputBuffer
 *    与 codec 内部异步 release 竞态, SIGSEGV 直接带走整进程.
 * 播放链路不崩, 是因为 ExoPlayer 走 DefaultRenderersFactory 的
 * EXTENSION_RENDERER_MODE_PREFER, ALAC 等轨优先命中 FFmpeg 扩展解码器, 根本不经厂商软解.
 *
 * 本类直驱同一套已在 APK 内的 FFmpeg 扩展(org.jellyfin.media3:media3-ffmpeg-decoder),
 * 与播放解码器同构. FfmpegAudioDecoder 是 package-private final 类, 仅构造与两个
 * getter 走反射; 其余交互全走 media3 公开 API(Decoder 接口 + Buffer 公开字段/方法).
 * 不建 ExoPlayer 实例: 播放器线程 + 音频焦点会与"边听边分析"互踩.
 * SimpleDecoder 自带解码线程, 错误全封在可 catch 的 DecoderException 里, 无状态机竞态.
 */
@Singleton
class FfmpegPcmDecoder @Inject constructor() {

    /** 解码产物: 已下混 mono 的 s16 样本 + 解码器实际输出采样率. */
    internal class Pcm16Mono(val pcm: ShortAccum, val sampleRate: Int)

    /**
     * FFmpeg 解码 extractor 当前选中音轨.
     *
     * 直驱 media3 FFmpeg 扩展的 package-private 解码器与 Format.Builder,
     * 属 UnstableApi 面(经反射隔离在 [FfmpegLibraryProbe]);lint 的
     * UnsafeOptInUsageError 在此处显式 opt-in 收敛(评审 2026-09-03 持续建议:
     * lint 纳入 check 管控后暴露的既有债务).
     *
     * @param format 该音轨的 MediaFormat(调用方已 selectTrack)
     * @param deadline 墙钟截止, 超时放弃(与调用方看门狗同款)
     * @return null = 扩展不可用 / native 初始化失败 / 解码出错 / 超时, 调用方决定回退
     */
    @OptIn(UnstableApi::class)
    internal fun decode(extractor: MediaExtractor, format: MediaFormat, deadline: Long): Pcm16Mono? {
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val sampleMimeType = ffmpegMime(mime) ?: return null
        if (!FfmpegLibraryProbe.available) {
            AppLog.warning(TAG, "ffmpeg lib unavailable, mime=$mime", null)
            return null
        }
        val srcSr = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (e: Exception) { 0 }
        val srcCh = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (e: Exception) { 0 }
        if (srcSr <= 0 || srcCh <= 0) {
            AppLog.warning(TAG, "ffmpeg skip: bad sr/ch $srcSr/$srcCh mime=$mime", null)
            return null
        }
        val maxInput = try { format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) } catch (e: Exception) { 0 }
        val initialInputSize = if (maxInput > 0) maxInput else DEFAULT_INPUT_SIZE

        val m3 = Format.Builder()
            .setSampleMimeType(sampleMimeType)
            .setChannelCount(srcCh)
            .setSampleRate(srcSr)
            .setInitializationData(csdData(format))
            .build()

        @Suppress("UNCHECKED_CAST")
        val decoder: Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, DecoderException> =
            try {
                FFDEC_CTOR.newInstance(m3, NUM_BUFFERS, NUM_BUFFERS, initialInputSize, false) as
                    Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, DecoderException>
            } catch (e: Throwable) {
                // 含 UnsatisfiedLinkError(so 加载失败)与"Initialization failed."
                AppLog.warning(TAG, "ffmpeg init failed mime=$mime: ${e.message}", null)
                return null
            }

        val pcm = ShortAccum()
        var eosIn = false
        var outCh = srcCh // 首帧输出后以解码器实际声道数为准
        var outChResolved = false
        var timedOut = false
        return try {
            while (true) {
                if (System.currentTimeMillis() > deadline) {
                    timedOut = true
                    break
                }
                if (!eosIn) {
                    val inBuf = decoder.dequeueInputBuffer()
                    if (inBuf != null) {
                        inBuf.clear()
                        inBuf.ensureSpaceForWrite(initialInputSize)
                        val target: ByteBuffer? = inBuf.data
                        val sampleSize =
                            if (target == null) -1 else extractor.readSampleData(target, 0)
                        if (sampleSize < 0) {
                            inBuf.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            inBuf.timeUs = 0L
                            if (target != null) {
                                target.position(0)
                                target.limit(0)
                            }
                            eosIn = true
                        } else {
                            checkNotNull(target).let {
                                it.position(0)
                                it.limit(sampleSize)
                            }
                            inBuf.timeUs = extractor.sampleTime
                            extractor.advance()
                        }
                        decoder.queueInputBuffer(inBuf)
                    }
                }
                val outBuf = decoder.dequeueOutputBuffer()
                if (outBuf == null) {
                    // 输入 EOS 已消费时 SimpleDecoder 必产出带 EOS flag 的输出, 不会停在 null;
                    // 真卡死由 deadline 兜底
                    Thread.sleep(POLL_MS)
                    continue
                }
                try {
                    if (!outChResolved) {
                        outCh = queryInt(decoder, FFDEC_GET_CH) ?: srcCh
                        if (outCh <= 0) outCh = srcCh
                        outChResolved = true
                    }
                    val data = outBuf.data
                    if (!outBuf.shouldBeSkipped && data != null && data.limit() > 0) {
                        consumeOutput(data, outCh, pcm)
                        if (pcm.size > EmotionAnalyzer.MAX_SAMPLES) {
                            AppLog.warning(
                                TAG,
                                "ffmpeg song too long (>${EmotionAnalyzer.MAX_SECONDS / 60}min), skip",
                                null,
                            )
                            return null
                        }
                    }
                    if (outBuf.isEndOfStream) break
                } finally {
                    outBuf.release()
                }
            }
            if (timedOut) {
                AppLog.warning(TAG, "ffmpeg decode timeout, abort mime=$mime", null)
                return null
            }
            val outSr = queryInt(decoder, FFDEC_GET_SR) ?: srcSr
            Pcm16Mono(pcm, if (outSr > 0) outSr else srcSr)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        } catch (e: DecoderException) {
            AppLog.warning(TAG, "ffmpeg decode failed mime=$mime: ${e.message}", null)
            null
        } catch (e: Exception) {
            AppLog.warning(TAG, "ffmpeg decode failed mime=$mime: ${e.message}", e)
            null
        } finally {
            try { decoder.release() } catch (ignored: Exception) { }
        }
    }

    /** 交织 s16 LE -> mono(多声道求均值下混). data 的 position/limit 已由解码器摆好. */
    private fun consumeOutput(data: ByteBuffer, ch: Int, pcm: ShortAccum) {
        data.order(ByteOrder.LITTLE_ENDIAN)
        val total = data.limit() / 2 / ch
        for (k in 0 until total) {
            if (ch == 1) {
                pcm.add(data.short)
            } else {
                var sum = 0
                for (c in 0 until ch) sum += data.short
                pcm.add((sum / ch).toShort())
            }
        }
    }

    companion object {
        private const val TAG = "FfmpegPcmDecoder"
        private const val NUM_BUFFERS = 16
        private const val DEFAULT_INPUT_SIZE = 5760
        private const val POLL_MS = 2L

        /** 该 mime 是否交给 FFmpeg 解(覆盖表 = FfmpegLibrary.getCodecName). */
        fun handles(mime: String?): Boolean = mime != null && ffmpegMime(mime) != null

        /**
         * FFmpeg 失败后是否还允许回退厂商 MediaCodec.
         * alac/mp3/g711 这类只有厂商软解可走, 恰是本次 SIGSEGV/死循环发源地, 禁回退;
         * aac/flac/opus/vorbis 有成熟公开解码链, 允许兜底.
         */
        fun mediaCodecFallbackSafe(mime: String?): Boolean = when (mime) {
            MimeTypes.AUDIO_FLAC, MimeTypes.AUDIO_OPUS, MimeTypes.AUDIO_VORBIS,
            MimeTypes.AUDIO_AAC,
            -> true
            else -> false
        }

        // 注意: MediaFormat.MIMETYPE_* 与 MimeTypes.* 常量值多有重合, when 分支只保留一份;
        // AAC 两者同值("audio/mp4a-latm"), 用 MimeTypes.AUDIO_AAC 即覆盖框架 mime.
        private fun ffmpegMime(mime: String): String? = when (mime) {
            MimeTypes.AUDIO_AAC -> MimeTypes.AUDIO_AAC
            MimeTypes.AUDIO_FLAC -> MimeTypes.AUDIO_FLAC
            "audio/alac" -> MimeTypes.AUDIO_ALAC
            "audio/vorbis" -> MimeTypes.AUDIO_VORBIS
            MimeTypes.AUDIO_OPUS -> MimeTypes.AUDIO_OPUS
            "audio/3gpp", "audio/amr-wb" -> mime
            "audio/g711-alaw", "audio/alaw" -> MimeTypes.AUDIO_ALAW
            "audio/g711-mlaw", "audio/mlaw" -> MimeTypes.AUDIO_MLAW
            "audio/mpeg" -> MimeTypes.AUDIO_MPEG
            MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC,
            MimeTypes.AUDIO_TRUEHD, MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD,
            -> mime
            else -> null
        }

        /** MediaFormat csd-0/1/2 -> Format.initializationData(ALAC cookie、vorbis header 等必需). */
        private fun csdData(format: MediaFormat): List<ByteArray> {
            val list = ArrayList<ByteArray>(3)
            for (key in arrayOf("csd-0", "csd-1", "csd-2")) {
                val bb = try { format.getByteBuffer(key) } catch (e: Exception) { null }
                if (bb != null) {
                    val dup = bb.duplicate()
                    dup.position(0)
                    val bytes = ByteArray(dup.remaining())
                    dup.get(bytes)
                    list.add(bytes)
                }
            }
            return list
        }

        private fun queryInt(target: Any, method: java.lang.reflect.Method): Int? = try {
            method.invoke(target) as? Int
        } catch (e: Exception) {
            null
        }

        // FfmpegAudioDecoder 为 package-private final: 构造与输出格式查询只能反射.
        private val FFDEC_CLASS: Class<*> by lazy {
            Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder")
        }

        private val FFDEC_CTOR: java.lang.reflect.Constructor<*> by lazy {
            FFDEC_CLASS.getDeclaredConstructor(
                Format::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType,
            ).also { it.isAccessible = true }
        }

        private val FFDEC_GET_SR: java.lang.reflect.Method by lazy {
            FFDEC_CLASS.getMethod("getSampleRate").also { it.isAccessible = true }
        }

        private val FFDEC_GET_CH: java.lang.reflect.Method by lazy {
            FFDEC_CLASS.getMethod("getChannelCount").also { it.isAccessible = true }
        }
    }
}

/** FfmpegLibrary.isAvailable 安全探针(so 缺失/加载失败=false, 不抛). */
private object FfmpegLibraryProbe {
    val available: Boolean by lazy {
        try {
            Class.forName("androidx.media3.decoder.ffmpeg.FfmpegLibrary")
                .getMethod("isAvailable")
                .invoke(null) as Boolean
        } catch (e: Throwable) {
            false
        }
    }
}
