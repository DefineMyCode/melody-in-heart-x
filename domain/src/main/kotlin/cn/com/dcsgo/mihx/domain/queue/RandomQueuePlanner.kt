package cn.com.dcsgo.mihx.domain.queue

/** Planner for normal (non-uniform) random order. */
interface RandomQueuePlanner {
    fun plan(songIds: List<Long>): List<Long>
}
