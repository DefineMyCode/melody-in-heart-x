package cn.com.dcsgo.mihx.player.mapper

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cn.com.dcsgo.mihx.core.model.Song
import javax.inject.Inject

/**
 * Maps a [Song] to a Media3 [MediaItem]. Invariant (plan §3.1): `MediaItem.mediaId` must equal
 * `Song.id.toString()` so the `mediaId -> Song.id` mapping stays unique and reversible.
 */
class SongMediaItemMapper @Inject constructor() {

    fun toMediaItem(song: Song): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .apply {
                if (song.albumArtUri != null) {
                    setArtworkUri(Uri.parse(song.albumArtUri))
                }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
