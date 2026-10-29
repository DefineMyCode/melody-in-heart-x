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

        // 评审 2026-10-27 · M-10：原实现把 `currentPlayOrderIndices()` 算了两遍
        // （orderedSongs 一遍 + orderedStartIndex 一遍），而它在 SHUFFLE 模式下
        // 本身就是 O(n²) —— 每次窗口规划（队列同步 / 切歌）都要付 2 × O(n²)。
        // 这里**算一次**，两个下游共用同一份结果。
        val orderIndices = queue.currentPlayOrderIndices()
        val orderedSongs = orderIndices
            .mapNotNull { queue.songs.getOrNull(it) }
            .ifEmpty { queue.songs }
        if (orderedSongs.isEmpty()) return null

        val orderedStartIndex = orderedStartIndex(orderIndices, requestedIndex, queue)
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

    private fun orderedStartIndex(
        orderIndices: List<Int>,
        requestedIndex: Int,
        queue: PlayQueue,
    ): Int {
        val safeRequestedIndex = requestedIndex.coerceIn(0, queue.songs.lastIndex)
        return orderIndices
            .indexOf(safeRequestedIndex)
            .takeIf { it >= 0 }
            ?: 0
    }
}
