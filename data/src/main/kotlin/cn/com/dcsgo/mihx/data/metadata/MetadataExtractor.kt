package cn.com.dcsgo.mihx.data.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.net.toUri
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.core.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Extracts playback metadata from an audio content URI via [MediaMetadataRetriever] (plan P5-A).
 * Runs on [Dispatchers.IO]; returns `null` when the file is unreadable so the caller can mark the
 * song as not playable instead of crashing the import.
 */
class MetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun extract(uri: String): Song? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri.toUri())
            Song(
                id = 0L,
                uri = uri,
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "",
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "",
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "",
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
                sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull() ?: 0,
                albumArtUri = null,
                titleOverride = null,
                playable = true,
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "metadata extract failed for $uri: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "MetadataExtractor"
    }
}
