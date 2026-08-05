package cn.com.dcsgo.mihx.data.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song
import java.io.File
import java.io.FileOutputStream

private const val TAG = "AlbumArtExtractor"
private const val CACHE_DIR_NAME = "album_art"
private const val TARGET_ALBUM_ART_PX = 512

object AlbumArtExtractor {

    /**
     * 获取歌曲封面 URI：
     * 1. 先从缓存文件读取（已提取过的）
     * 2. 再从 MediaStore album art 构造
     * 3. 最后尝试从 SAF 文件元数据提取并缓存
     * @param ctx  Application context
     * @param songUri  歌曲的 URI
     * @param songId   歌曲在仓库内的唯一 ID（用于缓存文件命名）
     * @return 封面 URI，无则返回 null
     */
    fun getAlbumArtUri(ctx: Context, songUri: Uri?, songId: Int): Uri? {
        if (songUri == null) return null

        // 1. 尝试从缓存文件读取
        val cachedUri = getCachedAlbumArt(ctx, songId)
        if (cachedUri != null) return cachedUri

        // 2. MediaStore URI：尝试读取 album art
        val mediaStoreUri = getMediaStoreAlbumArt(ctx, songUri)
        if (mediaStoreUri != null) return mediaStoreUri

        // 3. SAF URI：尝试从音频文件元数据提取并缓存
        return extractAndCacheAlbumArt(ctx, songUri, songId)
    }

    /**
     * 从缓存目录读取已保存的封面文件
     */
    private fun getCachedAlbumArt(ctx: Context, songId: Int): Uri? {
        val cacheDir = File(ctx.cacheDir, CACHE_DIR_NAME)
        val file = File(cacheDir, "album_$songId.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    /**
     * 如果 URI 来自 MediaStore，尝试从专辑表读取封面
     */
    private fun getMediaStoreAlbumArt(ctx: Context, songUri: Uri): Uri? {
        if (songUri.authority != "media") return null

        try {
            // 尝试读取 ALBUM_ID（需要 READ_MEDIA_AUDIO 权限）
            ctx.contentResolver.query(
                songUri,
                arrayOf(MediaStore.Audio.Media.ALBUM_ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    if (albumIdCol >= 0) {
                        val albumId = cursor.getLong(albumIdCol)
                        if (albumId > 0) {
                            val artUri = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            )
                            AppLog.debug(TAG, "MediaStore album art found: $artUri for albumId=$albumId")
                            return artUri
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "getMediaStoreAlbumArt failed: ${e.message}")
        }
        return null
    }

    /**
     * 用 MediaMetadataRetriever 从音频文件元数据中提取封面图，
     * 压缩后保存到缓存目录，返回 file:// URI
     */
    private fun extractAndCacheAlbumArt(ctx: Context, songUri: Uri, songId: Int): Uri? {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()

            // 方式1：直接用 URI（适用于 file:// 和部分 content:// URI）
            try {
                retriever.setDataSource(ctx, songUri)
            } catch (e: Exception) {
                AppLog.warning(TAG, "setDataSource(uri) failed, trying fd: ${e.message}")
                // 方式2：通过 ContentResolver 打开 fd（适用于 SAF content:// URI）
                try {
                    val fd = ctx.contentResolver.openFileDescriptor(songUri, "r")
                    if (fd != null) {
                        retriever.setDataSource(fd.fileDescriptor)
                        fd.close()
                    } else {
                        AppLog.warning(TAG, "openFileDescriptor returned null for $songUri")
                        return null
                    }
                } catch (e2: Exception) {
                    AppLog.warning(TAG, "setDataSource(fd) also failed for $songUri: ${e2.message}")
                    return null
                }
            }

            val artBytes = retriever.embeddedPicture
            if (artBytes == null || artBytes.isEmpty()) {
                AppLog.debug(TAG, "No embedded album art for $songUri")
                return null
            }

            // 先采样解码到 ~512px 量级，避免整幅超大内嵌封面导致内存尖峰
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, bounds)
            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= TARGET_ALBUM_ART_PX &&
                bounds.outHeight / (sampleSize * 2) >= TARGET_ALBUM_ART_PX
            ) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val original = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, decodeOptions)
                ?: return null

            val scaled = scaleBitmap(original, TARGET_ALBUM_ART_PX)
            // 只在确实创建了新 bitmap 时回收 original，避免双重回收
            if (scaled !== original) {
                original.recycle()
            }

            // 保存到缓存
            val cacheDir = File(ctx.cacheDir, CACHE_DIR_NAME)
            if (!cacheDir.exists()) cacheDir.mkdirs()

            val artFile = File(cacheDir, "album_$songId.jpg")
            FileOutputStream(artFile).use { fos ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            scaled.recycle()

            AppLog.info(TAG, "Album art cached: ${artFile.absolutePath}")
            return Uri.fromFile(artFile)
        } catch (e: Exception) {
            AppLog.warning(TAG, "extractAndCacheAlbumArt failed for $songUri: ${e.message}")
            return null
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    /**
     * 校验封面 URI 对应的文件是否存在，不存在则重新尝试提取
     * @return 有效的封面 URI，无则返回 null
     */
    fun refreshAlbumArtIfNeeded(ctx: Context, song: Song): Uri? {
        val currentUri = song.albumArtUri
        if (currentUri == null) {
            // 从没有尝试过提取封面，现在尝试
            return getAlbumArtUri(ctx, song.uri, song.id)
        }

        // 检查当前封面 URI 是否还有效
        if (currentUri.scheme == "file") {
            val file = File(currentUri.path ?: return null)
            if (file.exists()) return currentUri
            // 文件不存在，重新提取
            AppLog.debug(TAG, "Album art cache missing for song ${song.id}, re-extracting...")
            return getAlbumArtUri(ctx, song.uri, song.id)
        }

        // 非 file:// 的 URI（如 MediaStore albumart），直接返回
        return currentUri
    }

    /**
     * 将 Bitmap 等比缩放到 maxSize 范围内
     */
    private fun scaleBitmap(src: Bitmap, maxSize: Int): Bitmap {
        val scale = minOf(maxSize.toFloat() / src.width, maxSize.toFloat() / src.height, 1f)
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return if (w == src.width && h == src.height) src
        else Bitmap.createScaledBitmap(src, w, h, true)
    }
}
