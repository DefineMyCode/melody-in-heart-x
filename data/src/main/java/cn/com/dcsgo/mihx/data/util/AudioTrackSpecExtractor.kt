package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog

private const val TAG = "AudioTrackSpecExtractor"

/**
 * 轻量读取音频轨道规格：编码 MIME + 声道数。
 *
 * [android.media.MediaMetadataRetriever] 有 MIME 但没有声道数，这里用 [MediaExtractor]
 * 补一次轨道探测。位深走 MMR（`METADATA_KEY_BITS_PER_SAMPLE`），不在这里读。
 *
 * 探测失败返回 null，由调用方兜底为未知，不抛异常。
 */
object AudioTrackSpecExtractor {

    data class TrackSpec(
        val codec: String,
        val channelCount: Int,
    )

    fun read(ctx: Context, uri: Uri): TrackSpec? {
        var extractor: MediaExtractor? = null
        return try {
            extractor = MediaExtractor()
            if (!attachDataSource(ctx, uri, extractor)) return null

            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index)
                    .getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null

            val format = extractor.getTrackFormat(trackIndex)
            val codec = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).takeIf { it in 1..64 } ?: 0
            } else {
                0
            }
            TrackSpec(codec = codec, channelCount = channelCount)
        } catch (e: Exception) {
            AppLog.debug(TAG, "read failed for $uri: ${e.message}")
            null
        } finally {
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }

    /** SAF content:// 与 file:// 都尽量能打开；URI 方式失败时退回 fd */
    private fun attachDataSource(ctx: Context, uri: Uri, extractor: MediaExtractor): Boolean {
        return try {
            extractor.setDataSource(ctx, uri, null)
            true
        } catch (e: Exception) {
            val descriptor = try {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")
            } catch (_: Exception) {
                null
            } ?: return false

            descriptor.use {
                if (it.length >= 0) {
                    extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                } else {
                    extractor.setDataSource(it.fileDescriptor)
                }
            }
            true
        }
    }
}
