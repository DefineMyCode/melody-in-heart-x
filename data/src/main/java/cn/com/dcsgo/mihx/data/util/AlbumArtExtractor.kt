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
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "AlbumArtExtractor"
private const val CACHE_DIR_NAME = "album_art"
private const val TARGET_ALBUM_ART_PX = 512
/** 内嵌封面原始字节读取上限（字节）。超限视为异常封面不缓存，避免整幅超大图全量解码。 */
internal const val MAX_ART_BYTES = 8 * 1024 * 1024 // 8MB

object AlbumArtExtractor {

    // ── 封面缓存统计（验证按内容哈希去重的效果），导入前用 [resetCacheStats] 清零 ──
    private val cacheHitCounter = AtomicInteger(0)
    private val cacheWriteCounter = AtomicInteger(0)
    val cacheHitCount: Int get() = cacheHitCounter.get()
    val cacheWriteCount: Int get() = cacheWriteCounter.get()

    fun resetCacheStats() {
        cacheHitCounter.set(0)
        cacheWriteCounter.set(0)
    }

    /**
     * 获取歌曲封面 URI：
     * 1. 先从 MediaStore album art 构造
     * 2. 再从音频元数据提取内嵌封面，按内容哈希缓存（同图只解码/压缩/落盘一次，且不会盖错封面）
     * @param ctx  Application context
     * @param songUri  歌曲的 URI
     * @param preExtractedBytes 元数据提取时已拿到内嵌封面字节；传了就不再二次打开 MMR
     * @return 封面 URI，无则返回 null
     */
    fun getAlbumArtUri(
        ctx: Context,
        songUri: Uri?,
        preExtractedBytes: ByteArray? = null,
    ): Uri? {
        if (songUri == null) return null

        // 1. MediaStore URI：尝试读取 album art
        val mediaStoreUri = getMediaStoreAlbumArt(ctx, songUri)
        if (mediaStoreUri != null) return mediaStoreUri

        // 2. SAF URI：从元数据取封面字节，按内容哈希缓存。
        //    调用方已在提取元数据时打开过 MMR 拿到内嵌封面字节，直接复用可省一次 setDataSource
        val artBytes = preExtractedBytes ?: readEmbeddedArtBytes(ctx, songUri) ?: return null
        return cacheArtBytes(ctx, artBytes)
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
     * 用 MediaMetadataRetriever 从音频文件元数据中读取内嵌封面原始字节。
     * @return 内嵌封面字节（不超过 [MAX_ART_BYTES]），无封面或读取失败返回 null
     */
    private fun readEmbeddedArtBytes(ctx: Context, songUri: Uri): ByteArray? {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()

            // 方式1：直接用 URI（适用于 file:// 和部分 content:// URI）
            try {
                retriever.setDataSource(ctx, songUri)
            } catch (e: Exception) {
                AppLog.warning(TAG, "setDataSource(uri) failed, trying fd: ${e.message}")
                // 方式2：通过 ContentResolver 打开 fd（适用于 SAF content:// URI）。
                // m1（评审 2026-09-03）：必须 use{} —— setDataSource 抛异常时 fd 会泄漏。
                try {
                    val fd = ctx.contentResolver.openFileDescriptor(songUri, "r")
                    if (fd != null) {
                        fd.use { parcel ->
                            retriever.setDataSource(parcel.fileDescriptor)
                        }
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
            if (artBytes.size > MAX_ART_BYTES) {
                AppLog.warning(TAG, "Embedded album art ${artBytes.size} exceeds ${MAX_ART_BYTES} bytes, skipped")
                return null
            }
            return artBytes
        } catch (e: Exception) {
            AppLog.warning(TAG, "readEmbeddedArtBytes failed for $songUri: ${e.message}")
            return null
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
        }
    }

    /**
     * 把已提取的内嵌封面字节按内容哈希缓存；同哈希命中则直接复用缓存文件，跳过解码/压缩/写盘。
     * @return file:// URI（缓存文件），无则返回 null
     */
    private fun cacheArtBytes(ctx: Context, artBytes: ByteArray): Uri? {
        val cacheDir = File(ctx.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val hash = contentHash(artBytes)
        val artFile = File(cacheDir, "art_$hash.jpg")
        if (artFile.exists()) {
            cacheHitCounter.incrementAndGet()
            AppLog.debug(TAG, "Album art cache hit: ${artFile.name}")
            return Uri.fromFile(artFile)
        }

        // 先采样解码到 ~512px 量级，避免整幅超大内嵌封面导致内存尖峰
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, bounds)
        // 解码器无法给出尺寸时拒绝解码（避免 sampleSize=1 整幅原稿解码）
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            AppLog.warning(TAG, "Album art bounds undecodable ($bounds), skipped")
            return null
        }
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

        FileOutputStream(artFile).use { fos ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        }
        scaled.recycle()

        cacheWriteCounter.incrementAndGet()
        AppLog.info(TAG, "Album art cached: ${artFile.absolutePath}")
        return Uri.fromFile(artFile)
    }

    /** 封面原始字节的内容哈希（SHA-1），用作按内容去重的缓存文件名 */
    private fun contentHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    /**
     * 校验封面 URI 对应的文件是否存在，不存在则重新尝试提取
     * @return 有效的封面 URI，无则返回 null
     */
    fun refreshAlbumArtIfNeeded(ctx: Context, song: Song): Uri? {
        val currentUri = song.albumArtUri
        if (currentUri == null) {
            // 从没有尝试过提取封面，现在尝试
            return getAlbumArtUri(ctx, song.uri)
        }

        // 检查当前封面 URI 是否还有效
        if (currentUri.scheme == "file") {
            val file = File(currentUri.path ?: return null)
            if (file.exists()) return currentUri
            // 文件不存在，重新提取
            AppLog.debug(TAG, "Album art cache missing for song ${song.id}, re-extracting...")
            return getAlbumArtUri(ctx, song.uri)
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
