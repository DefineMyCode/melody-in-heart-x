package cn.com.dcsgo.mihx.data.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import cn.com.dcsgo.mihx.core.model.Song

object SongMediaItemMapper {

    fun toMediaItem(song: Song): MediaItem? {
        val uri = song.uri ?: return null
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(uri)
            .setMediaMetadata(toMediaMetadata(song))
            .build()
    }

    fun toMediaItems(songs: List<Song>): List<MediaItem> {
        return songs.mapNotNull { toMediaItem(it) }
    }

    private fun toMediaMetadata(song: Song): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)

        song.albumArtUri?.let { builder.setArtworkUri(it) }

        return builder.build()
    }
}
