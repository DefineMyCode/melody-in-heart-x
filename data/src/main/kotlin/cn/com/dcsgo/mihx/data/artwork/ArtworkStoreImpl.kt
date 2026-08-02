package cn.com.dcsgo.mihx.data.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [ArtworkStore]. Reads the embedded picture via [MediaMetadataRetriever.getEmbeddedPicture]
 * and writes a downscaled JPEG (long edge <= [MAX_EDGE_PX]) into `cacheDir/artwork/<songId>.jpg`.
 * Runs on [Dispatchers.IO]; any failure yields null so an unreadable/false-positive cover never
 * aborts the import.
 */
@Singleton
class ArtworkStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ArtworkStore {

    override suspend fun extractAndCache(audioUri: String, songId: Long): String? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, audioUri.toUri())
            val raw = retriever.embeddedPicture ?: return@withContext null
            val bitmap = decodeBounded(raw, MAX_EDGE_PX) ?: return@withContext null
            val dir = File(context.cacheDir, ARTWORK_DIR).apply { if (!exists()) mkdirs() }
            val file = File(dir, "$songId.jpg")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            if (!bitmap.isRecycled) bitmap.recycle()
            FileProvider.getUriForFile(context, ARTWORK_FILE_PROVIDER_AUTHORITY, file).toString()
        } catch (e: Exception) {
            AppLogger.w(TAG, "artwork extract failed for $audioUri: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Decodes [bytes] to at most a [maxEdge] long-edge bitmap via `inSampleSize` downscaling. */
    private fun decodeBounded(bytes: ByteArray, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val (w, h) = bounds.outWidth to bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val sample = maxOf(1, maxOf(w, h) / maxEdge)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private companion object {
        const val TAG = "ArtworkStore"
        const val MAX_EDGE_PX = 512
        const val JPEG_QUALITY = 82
        const val ARTWORK_DIR = "artwork"
    }
}
