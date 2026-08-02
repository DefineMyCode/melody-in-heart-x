package cn.com.dcsgo.mihx.data.database.entity

/** Read-only projection: playlist row plus the album-art of its most recently added song. */
data class PlaylistCoverRow(
    val id: Long,
    val name: String,
    val coverUri: String?,
)
