package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song

/**
 * Computes a bounded window around the current item over an already-expanded full play order.
 *
 * - Window spans [lookBehind] items before and [lookAhead] items after the current position
 *   (the current item itself is always included); at most [MAX_WINDOW_SIZE] items.
 * - Unplayable items (default: `uri == null`) are dropped from the window. If the requested
 *   current item is unplayable, it is first snapped to the nearest playable item (forward, then
 *   backward) so the window always has a valid current [Song].
 *
 * Pure & side-effect free — fully unit-testable (plan P2-5 / P2-10).
 */
class PlaybackWindowPlanner(
    private val lookBehind: Int = DEFAULT_LOOK_BEHIND,
    private val lookAhead: Int = DEFAULT_LOOK_AHEAD,
) {
    fun plan(
        fullOrder: List<Song>,
        currentIndex: Int,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): PlaybackWindow {
        if (fullOrder.isEmpty()) {
            return PlaybackWindow(emptyList(), -1, 0, 0, 0)
        }
        val safeCurrent = snapToPlayable(
            fullOrder,
            currentIndex.coerceIn(0, fullOrder.lastIndex),
            isPlayable,
        )
        val windowStart = (safeCurrent - lookBehind).coerceAtLeast(0)
        val windowEnd = (safeCurrent + lookAhead).coerceAtMost(fullOrder.lastIndex)
        val windowItems = fullOrder.subList(windowStart, windowEnd + 1).filter { isPlayable(it) }
        val currentSong = fullOrder[safeCurrent]
        val finalItems = if (windowItems.any { it == currentSong }) windowItems else listOf(currentSong) + windowItems
        val currentPosInWindow = finalItems.indexOf(currentSong).coerceAtLeast(0)
        return PlaybackWindow(
            items = finalItems,
            currentIndexInWindow = currentPosInWindow,
            startIndexInFullOrder = windowStart,
            endIndexExclusiveInFullOrder = windowEnd + 1,
            currentIndexInFullOrder = safeCurrent,
        )
    }

    private fun snapToPlayable(fullOrder: List<Song>, index: Int, isPlayable: (Song) -> Boolean): Int {
        if (isPlayable(fullOrder[index])) return index
        // Prefer the next playable item AFTER the current one (plan P2-7).
        for (delta in 1..(fullOrder.lastIndex - index)) {
            if (isPlayable(fullOrder[index + delta])) return index + delta
        }
        // Fall back to the nearest playable item BEFORE it.
        for (delta in 1..index) {
            if (isPlayable(fullOrder[index - delta])) return index - delta
        }
        return index
    }

    companion object {
        const val DEFAULT_LOOK_BEHIND = 20
        const val DEFAULT_LOOK_AHEAD = 50
        const val MAX_WINDOW_SIZE = 71
    }
}

/**
 * [startIndexInFullOrder] until [endIndexExclusiveInFullOrder] is the half-open slice of the
 * *unfiltered* full order this window was cut from; [items] are the playable survivors of that
 * slice. Keeping the raw range lets the synchronizer diff two consecutive windows by pure index
 * arithmetic instead of matching (possibly repeated) song ids.
 */
data class PlaybackWindow(
    val items: List<Song>,
    val currentIndexInWindow: Int,
    val startIndexInFullOrder: Int,
    val endIndexExclusiveInFullOrder: Int,
    val currentIndexInFullOrder: Int,
)
