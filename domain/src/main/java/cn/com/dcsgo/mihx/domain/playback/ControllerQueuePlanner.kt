package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

/**
 * Builds the ordered queue that is handed to the platform playback controller.
 *
 * The business queue keeps the complete UI-visible queue. This planner converts it
 * into the playback order expected by the controller without depending on Media3.
 */
object ControllerQueuePlanner {
    fun plan(
        queue: PlayQueue,
        requestedIndex: Int = queue.currentIndex,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): ControllerQueuePlan? {
        if (queue.isEmpty) return null

        val orderedSongs = orderedSongs(queue)
        if (orderedSongs.isEmpty()) return null

        val orderedStartIndex = orderedStartIndex(queue, requestedIndex)
        val playable = orderedSongs.withIndex()
            .filter { (_, song) -> isPlayable(song) }
        if (playable.isEmpty()) return null

        val playableStartIndex = playable.indexOfFirst { it.index == orderedStartIndex }
            .takeIf { it >= 0 }
            ?: playable.indexOfFirst { it.index > orderedStartIndex }.takeIf { it >= 0 }
            ?: playable.lastIndex

        return ControllerQueuePlan(
            songs = playable.map { it.value },
            startIndex = playableStartIndex,
        )
    }

    private fun orderedSongs(queue: PlayQueue): List<Song> {
        return queue.currentPlayOrderIndices()
            .mapNotNull { queue.songs.getOrNull(it) }
            .ifEmpty { queue.songs }
    }

    private fun orderedStartIndex(
        queue: PlayQueue,
        requestedIndex: Int,
    ): Int {
        val safeRequestedIndex = requestedIndex.coerceIn(0, queue.songs.lastIndex)
        return queue.currentPlayOrderIndices()
            .indexOf(safeRequestedIndex)
            .takeIf { it >= 0 }
            ?: 0
    }
}
