package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue

interface ControllerQueuePlannerPort {
    fun plan(queue: PlayQueue, requestedIndex: Int = queue.currentIndex): ControllerQueuePlan?
}
