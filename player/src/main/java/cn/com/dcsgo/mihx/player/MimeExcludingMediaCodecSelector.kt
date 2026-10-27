package cn.com.dcsgo.mihx.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/**
 * 平台解码器候选筛选器：把 [excludedMimes] 从候选中剔除。
 *
 * Media3 的 `MediaCodecAudioRenderer.supportsFormat()` 会通过本选择器查询候选解码器，
 * 候选为空即判定该渲染器**不支持**该 mime（`RendererCapabilities.create(1)`）。
 * 配合 `EXTENSION_RENDERER_MODE_ON`（平台 MediaCodec 优先、FFmpeg 扩展兜底），
 * 被剔除的 mime 会自动落到渲染器列表靠后的 FFmpeg 扩展渲染器上。
 *
 * 失败模式是安全的：即使本选择器因任何原因不生效，结果也只是退回「平台优先」的
 * 默认行为，不会让音频彻底无解码器可用。
 */
@OptIn(UnstableApi::class)
internal class MimeExcludingMediaCodecSelector(
    private val delegate: MediaCodecSelector,
    private val excludedMimes: Set<String>,
) : MediaCodecSelector {

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunneling: Boolean,
    ): List<MediaCodecInfo> {
        if (mimeType in excludedMimes) return emptyList()
        return delegate.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunneling)
    }
}
