package cn.com.dcsgo.mihx.domain.queue

/**
 * Cached window of media items currently loaded into the transport queue.
 *
 * [startIndex] and [endIndexExclusive] address the *unfiltered* full play order, while [mediaIds]
 * holds only the playable items actually handed to the transport. Both are needed: the range drives
 * incremental window slides (see WindowSlidePlanner), the ids describe the live transport queue.
 */
data class PlaybackWindowState(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val mediaIds: List<String>,
    val windowVersion: Long,
)
