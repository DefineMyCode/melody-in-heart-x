package cn.com.dcsgo.mihx.domain.queue

import java.util.Random
import javax.inject.Inject

/** Default [RandomQueuePlanner]: plain Fisher–Yates shuffle of the given ids. */
class DefaultRandomQueuePlanner @Inject constructor() : RandomQueuePlanner {
    private val random: Random = Random()

    override fun plan(songIds: List<Long>): List<Long> = songIds.shuffled(random)
}
