package cn.com.dcsgo.mihx.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

object PlayerFactory {

    /**
     * 不走平台解码器、强制由 FFmpeg 扩展解码的 mime。
     *
     * ALAC 是唯一必须排除的：真机 SIGSEGV（2026-08-31，小米高通 `c2.qti.alac.sw.decoder`）
     * 之后的规避手段就是「ALAC 等轨优先命中 FFmpeg 扩展解码器，根本不经厂商软解」
     * （见 [FfmpegPcmDecoder] 的类注释）。渲染器改成平台优先后若不排除 ALAC，
     * 等于把这个已验证的规避措施悄悄撤掉。
     *
     * 其余格式（FLAC / Vorbis / Opus / AAC / MP3 等）交给平台解码器：
     * AOSP 自带软件解码器，比厂商软解稳定，且输出定长 16bit PCM。
     * WMA / APE 平台本就没有解码器，仍由 FFmpeg 扩展兜底。
     */
    private val FFMPEG_ONLY_MIMES = setOf(
        MimeTypes.AUDIO_ALAC, // "audio/alac"
        "audio/x-lg-alac", // 部分厂商上报的 ALAC 别名
    )

    @OptIn(UnstableApi::class)
    fun create(
        context: Context,
        loadControl: LoadControl? = null,
        handleAudioFocus: Boolean = true,
        handleAudioBecomingNoisy: Boolean = true,
        enableExtensionRenderers: Boolean = true,
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            // 关闭浮点输出。Media3 1.9 的浮点输出只对 24/32bit PCM 源生效
            // （Util.isEncodingHighResolutionPcm 不含 16bit），16bit 源本就不走浮点；
            // 且 Media3 只有 ToFloatPcmAudioProcessor、没有 float→int16 的反向处理器，
            // 所以 FFmpeg 扩展解出的 float 也不会被降级成 16bit。
            // 结论：本项对空间音频判定（Android Spatializer 只接受 PCM_16BIT）无影响，
            // 关闭只是让 24/32bit 源保持整数 PCM，规避个别 ROM 上 float AudioTrack 的兼容问题。
            setEnableAudioFloatOutput(false)
            setEnableDecoderFallback(true)
            if (enableExtensionRenderers) {
                // ON（不是 PREFER）：平台 MediaCodec 解码器优先，FFmpeg 扩展退居兜底。
                //
                // 这是杜比内容能走通设备原生 Atmos 渲染的前提：
                //  - FFmpeg 扩展的 FfmpegLibrary 认领了 audio/eac3-joc / audio/true-hd，
                //    而它编进去的只有 ff_eac3_decoder / ff_truehd_decoder（无 JOC 解码器），
                //    只能解出基础 5.1/7.1，**Atmos 对象层（JOC）被丢弃**；
                //  - 平台侧则由 MediaCodecUtil.getAlternativeCodecMimeType 把 audio/eac3-joc
                //    映射到设备的 audio/eac3 解码器（即杜比 Atmos 渲染器），Atmos 才成立。
                //
                // 代价：ALAC 等格式的解码器归属会变，需要用 selector 排除回 FFmpeg（见下）。
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                // 平台优先会改变 ALAC 的解码器归属；用 selector 把 ALAC 排除回 FFmpeg 扩展，
                // 保住 2026-08-31 之后验证过的规避措施。扩展不可用时不做排除
                // （否则 ALAC 会既没有平台解码器也没有扩展解码器，直接播不了）。
                if (FfmpegLibraryProbe.available) {
                    setMediaCodecSelector(
                        MimeExcludingMediaCodecSelector(MediaCodecSelector.DEFAULT, FFMPEG_ONLY_MIMES)
                    )
                }
            }
        }

        val actualLoadControl = loadControl ?: DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                4000,
                10000,
                2000,
                2500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(actualLoadControl)
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    handleAudioFocus
                )
                setHandleAudioBecomingNoisy(handleAudioBecomingNoisy)
                setPauseAtEndOfMediaItems(false)
            }
    }
}
