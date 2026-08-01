package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caches the last applied [PlaybackWindowState] and decides whether the transport queue must be
 * rebuilt (a new [ControllerQueuePlan] applied to MediaController) or can be reused.
 *
 * Replan triggers: no cache yet, or the current item drifts within [DEFAULT_REPLAN_THRESHOLD]
 * positions of either window edge (so the window can be re-centered before playback runs off the
 * end). When reuse is possible [WindowResolution.plan] is null and the caller must NOT call
 * `setMediaItems` again — this is what keeps 1000-song queue switches under 300ms (plan R1/P2-6).
 */
@Singleton
class ControllerWindowSynchronizer @Inject constructor(
    private val planner: WindowedControllerQueuePlanner,
) {
    private val replanThreshold = DEFAULT_REPLAN_THRESHOLD
    private var cached: PlaybackWindowState? = null

    fun current(): PlaybackWindowState? = cached

    fun forceReplan() {
        cached = null
    }

    fun resolve(playQueue: PlayQueue): WindowResolution {
        val cache = cached
        val current = playQueue.currentIndex
        if (cache != null && isSafelyInside(cache, current)) {
            return WindowResolution(
                plan = null,
                window = cache,
                currentIndexInWindow = current - cache.startIndex,
            )
        }
        val plan = planner.plan(playQueue)
        val newCache = PlaybackWindowState(
            startIndex = plan.windowStartIndex,
            mediaIds = plan.mediaItems.map { it.mediaId },
            windowVersion = (cache?.windowVersion ?: 0L) + 1L,
        )
        cached = newCache
        return WindowResolution(
            plan = plan,
            window = newCache,
            currentIndexInWindow = plan.currentIndex,
        )
    }

    private fun isSafelyInside(cache: PlaybackWindowState, current: Int): Boolean {
        if (cache.mediaIds.isEmpty()) return false
        val pos = current - cache.startIndex
        return pos >= replanThreshold && pos <= cache.mediaIds.size - 1 - replanThreshold
    }

    companion object {
        const val DEFAULT_REPLAN_THRESHOLD = 10
    }
}

data class WindowResolution(
    val plan: ControllerQueuePlan?,
    val window: PlaybackWindowState,
    val currentIndexInWindow: Int,
)
