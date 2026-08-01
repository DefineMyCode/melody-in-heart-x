package cn.com.dcsgo.mihx.core.model

/** Extended, possibly heavy metadata for a [Song] (parsed lazily). */
data class SongInfo(
    val songId: Long,
    val bitrate: Int = 0,
    val channels: Int = 0,
    val mimeType: String? = null,
)
