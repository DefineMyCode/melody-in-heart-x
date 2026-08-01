package cn.com.dcsgo.mihx.domain.queue

/** Planner for global uniform-random; weights come from play counts. */
interface UniformRandomPlanner {
    fun plan(songIds: List<Long>, playCounts: Map<Long, Long>): List<Long>
}
