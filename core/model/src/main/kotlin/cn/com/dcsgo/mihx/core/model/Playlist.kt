package cn.com.dcsgo.mihx.core.model

/** A user playlist (song membership is a many-to-many cross reference). */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long = 0L,
)

/**
 * Playlist list-row view: playlist metadata plus the album-art of its most recently added song
 * (null when the playlist is empty or that song has no art — the UI falls back to a default icon).
 */
data class PlaylistWithCover(
    val id: Long,
    val name: String,
    val coverUri: String?,
)
