package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort

class WindowedControllerQueuePlanner(
    private val synchronizer: ControllerWindowSynchronizer = ControllerWindowSynchronizer(),
) : ControllerQueuePlannerPort {
    override fun plan(queue: PlayQueue, requestedIndex: Int): ControllerQueuePlan? {
        return synchronizer.planControllerQueue(
            queue = queue.withCurrentIndex(requestedIndex),
            force = true,
        )
    }
}
