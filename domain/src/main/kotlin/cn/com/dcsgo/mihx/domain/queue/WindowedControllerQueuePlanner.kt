package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue
import javax.inject.Inject

/**
 * Implements [ControllerQueuePlannerPort]: expands the [PlayQueue] into the full ordered [Song]
 * list, then takes only the bounded window around the current item.
 */
class WindowedControllerQueuePlanner @Inject constructor(
    private val windowPlanner: PlaybackWindowPlanner,
) : ControllerQueuePlannerPort {
    override fun plan(playQueue: PlayQueue): ControllerQueuePlan {
        val fullOrder = ControllerQueuePlanner.expand(playQueue)
        val window = windowPlanner.plan(fullOrder, playQueue.currentIndex)
        return ControllerQueuePlan(
            mediaItems = window.items.map { song ->
                ControllerQueuePlan.PlannedItem(
                    mediaId = song.id.toString(),
                    uri = song.uri,
                    song = song,
                )
            },
            currentIndex = window.currentIndexInWindow,
            windowStartIndex = window.startIndexInFullOrder,
        )
    }
}
