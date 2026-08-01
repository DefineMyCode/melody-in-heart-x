package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

/**
 * Expands a [PlayQueue] into the full, ordered list of [Song]s. Order follows
 * [PlayQueue.playOrderIds], resolved back to queue indices via [PlayQueue.currentPlayOrderIndices]
 * (which preserves repeated ids). No dedup — duplicates remain as separate positions.
 */
object ControllerQueuePlanner {
    fun expand(playQueue: PlayQueue): List<Song> {
        val indices = playQueue.currentPlayOrderIndices()
        return indices.mapNotNull { idx -> playQueue.songs.getOrNull(idx) }
    }
}
