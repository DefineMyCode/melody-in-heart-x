@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue

/**
 * Builds ordered play-order id lists. Random order must go through
 * [UniformRandomPlanner]; PlayQueue must NOT depend on the repository directly.
 */
object QueueManager {
    fun PlayOrderBuilder(playQueue: PlayQueue): Builder = Builder(playQueue)

    class Builder(private val base: PlayQueue) {
        private var ids: List<Long> = base.playOrderIds

        fun sequential(): Builder = apply { ids = base.songs.map { it.id } }
        fun reverse(): Builder = apply { ids = base.songs.map { it.id }.asReversed() }
        fun withIds(next: List<Long>): Builder = apply { ids = next }

        fun build(): PlayQueue = base.copy(playOrderIds = ids, playMode = base.playMode)
    }
}
