package cn.com.dcsgo.mihx.domain.queue

import java.util.Random
import javax.inject.Inject

/**
 * Default [UniformRandomPlanner]: weighted shuffle where lower play counts are biased to appear
 * earlier. With empty [playCounts] it degrades to a plain shuffle. Deterministic for a given
 * [Random] seed (testable).
 */
class DefaultUniformRandomPlanner @Inject constructor() : UniformRandomPlanner {
    private val random: Random = Random()

    override fun plan(songIds: List<Long>, playCounts: Map<Long, Long>): List<Long> {
        if (songIds.isEmpty()) return emptyList()
        val weights = songIds.map { id ->
            val count = playCounts[id] ?: 0L
            val weight = 1.0 / (count + 1L).toDouble()
            random.nextDouble() * weight
        }
        return songIds.mapIndexed { index, id -> id to weights[index] }
            .sortedByDescending { it.second }
            .map { it.first }
    }
}
