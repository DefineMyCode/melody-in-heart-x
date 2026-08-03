package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan

/**
 * Builds the ordered queue that is handed to MediaController.
 *
 * MediaController is the source of truth for actual playback order. PlayQueue keeps the
 * full business queue and UI order; this planner is the single bridge between the two.
 */
object ControllerQueuePlanner {
    fun plan(
        queue: PlayQueue,
        requestedIndex: Int = queue.currentIndex,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): ControllerQueuePlan? {
        return cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlanner.plan(
            queue = queue,
            requestedIndex = requestedIndex,
            isPlayable = isPlayable,
        )
    }
}
