package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity

/**
 * Join row linking a playlist to its songs, preserving manual ordering via [position].
 * Composite primary key keeps a song from being added twice to the same playlist.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
)
data class PlaylistSongCrossRefEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int = 0,
)
