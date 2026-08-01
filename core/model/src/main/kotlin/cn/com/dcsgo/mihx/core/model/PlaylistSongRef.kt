package cn.com.dcsgo.mihx.core.model

/** Cross-reference row linking a playlist to a song at an ordered position. */
data class PlaylistSongRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)
