package cn.com.dcsgo.mihx.domain.queue

/** Cached window of media items currently loaded into the transport queue. */
data class PlaybackWindowState(
    val startIndex: Int,
    val mediaIds: List<String>,
    val windowVersion: Long,
)
