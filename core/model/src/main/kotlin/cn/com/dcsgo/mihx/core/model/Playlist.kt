package cn.com.dcsgo.mihx.core.model

/** A user playlist (song membership is a many-to-many cross reference). */
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long = 0L,
)
