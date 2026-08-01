package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue

/** Port implemented by the :player module's windowed planner. */
interface ControllerQueuePlannerPort {
    fun plan(playQueue: PlayQueue): ControllerQueuePlan
}
