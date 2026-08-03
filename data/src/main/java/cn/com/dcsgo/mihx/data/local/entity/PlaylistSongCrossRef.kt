package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songId"],
    indices = [Index("songId")],
)
data class PlaylistSongCrossRef(
    val playlistId: Int,
    val songId: Int,
    val sortOrder: Int,
)
