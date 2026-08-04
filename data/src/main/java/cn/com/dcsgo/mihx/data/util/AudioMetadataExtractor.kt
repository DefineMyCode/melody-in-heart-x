package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.SongInfo
import java.io.File

private const val TAG = "AudioMetadataExtractor"

/**
 * 音频元数据提取器
 * 从音频文件中提取标题、艺术家等元数据信息
 */
object AudioMetadataExtractor {

    /** 导入时提取的基础元数据 */
    data class ExtractedMetadata(
        val title: String,
        val artist: String,
        val album: String,
        val sampleRate: Int,
        val durationMs: Long = 0L,
    )

    /**
     * 从音频文件提取元数据
     * @param ctx Application context
     * @param uri 音频文件的 URI
     * @param fallbackTitle 如果没有获取到标题，使用此默认值
     * @return [ExtractedMetadata] 标题、艺术家、专辑、采样率
     */
    fun extractMetadata(
        ctx: Context,
        uri: Uri,
        fallbackTitle: String
    ): ExtractedMetadata {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackTitle

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: "未知艺术家"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
                ?: ""

            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull() ?: 0

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            AppLog.debug(
                TAG,
                "extractMetadata: title=$title, artist=$artist, album=$album, sampleRate=$sampleRate, durationMs=$durationMs"
            )
            return ExtractedMetadata(title = title, artist = artist, album = album, sampleRate = sampleRate, durationMs = durationMs)
        } catch (e: Exception) {
            AppLog.warning(TAG, "extractMetadata failed for $uri: ${e.message}")
            return ExtractedMetadata(title = fallbackTitle, artist = "未知艺术家", album = "", sampleRate = 0)
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    /**
     * 提取完整的音频元数据（用于歌曲信息展示）
     * @return SongInfo 包含标题、艺术家、专辑、时长、比特率、采样率、格式、文件大小、文件路径
     */
    fun extractFullMetadata(ctx: Context, uri: Uri): SongInfo {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(ctx, uri)

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: ""

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.takeIf { it.isNotBlank() } ?: "未知艺术家"

            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "未知专辑"

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull() ?: 0L

            val sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull() ?: 0

            return SongInfo(
                title = title,
                artist = artist,
                album = album,
                duration = formatDuration(durationMs),
                bitRate = if (bitRate > 0) "${bitRate / 1000} kbps" else "未知",
                sampleRate = if (sampleRate > 0) "$sampleRate Hz" else "未知",
                format = extractFormat(uri),
                fileSize = getFileSize(ctx, uri),
                filePath = getRealPathFromUri(ctx, uri)
            )
        } catch (e: Exception) {
            AppLog.warning(TAG, "extractFullMetadata failed for $uri: ${e.message}")
            return SongInfo(filePath = uri.toString())
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    /** 格式化时长：毫秒 → mm:ss */
    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "未知"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /** 从 URI 提取文件格式/扩展名 */
    private fun extractFormat(uri: Uri): String {
        val path = uri.lastPathSegment ?: return "未知"
        val dotIndex = path.lastIndexOf('.')
        return if (dotIndex >= 0) path.substring(dotIndex + 1).uppercase() else "未知"
    }

    /** 获取文件大小（通过 ContentResolver query OpenableColumns.SIZE） */
    private fun getFileSize(ctx: Context, uri: Uri): String {
        return try {
            val size = ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            } ?: -1L
            if (size > 0) formatFileSize(size) else "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    /** 格式化文件大小 */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * 尝试从 URI 获取真实文件路径。
     * 支持 SAF content URI、MediaStore URI、file:// URI。
     * 如果无法解析，则返回原始 URI 字符串。
     */
    private fun getRealPathFromUri(ctx: Context, uri: Uri): String {
        // 1. file:// 方案：直接取路径
        if (uri.scheme == "file") {
            return uri.path ?: uri.toString()
        }

        // 2. MediaStore content:// 方案
        if (uri.authority == "media") {
            return try {
                val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                ctx.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    val dataIndex = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                    if (cursor.moveToFirst()) {
                        val path = cursor.getString(dataIndex)
                        if (path.isNotBlank()) return path
                    }
                }
                uri.toString()
            } catch (e: Exception) {
                AppLog.warning(TAG, "MediaStore path query failed: ${e.message}")
                uri.toString()
            }
        }

        // 3. SAF DocumentsContract URI（com.android.externalstorage.documents 等）
        return try {
            val docId = DocumentsContract.getDocumentId(uri) // 例如 "primary:Music/song.mp3"
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) {
                val type = parts[0] // "primary" 等
                val relativePath = parts[1] // "Music/song.mp3"
                if (type == "primary") {
                    val fullPath = "${Environment.getExternalStorageDirectory()}/$relativePath"
                    // 验证文件确实存在
                    if (File(fullPath).exists()) {
                        return fullPath
                    }
                } else {
                    // 非 primary 存储（SD 卡等）
                    val storageDir = getStoragePath(type)
                    if (storageDir != null) {
                        val fullPath = "$storageDir/$relativePath"
                        if (File(fullPath).exists()) {
                            return fullPath
                        }
                    }
                }
            }
            // 如果 DocumentId 解析失败，尝试从 URI path 直接解码
            val decodedPath = uri.path?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            if (decodedPath != null && decodedPath.contains("/storage/")) {
                // 从 /tree/.../... 中提取实际路径
                val treePath = decodedPath.substringAfter("/tree/")
                    .replace(":","/")
                    .replace("%3A", "/")
                    .replace("%2F", "/")
                    .replace("%20", " ")
                val fullPath = "/storage/emulated/0/$treePath"
                if (File(fullPath).exists()) return fullPath
            }
            uri.toString()
        } catch (e: Exception) {
            AppLog.warning(TAG, "SAF URI path resolution failed: ${e.message}")
            uri.toString()
        }
    }

    /**
     * 根据存储类型获取挂载路径（如 SD 卡）
     */
    private fun getStoragePath(type: String): String? {
        // 遍历可能的挂载点
        val candidates = listOf(
            "/storage/$type",
            "/mnt/media_rw/$type",
            "/mnt/$type"
        )
        for (path in candidates) {
            if (File(path).exists()) return path
        }
        return null
    }
}
