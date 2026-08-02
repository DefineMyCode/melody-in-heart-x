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
    private val slidePlanner: WindowSlidePlanner,
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
        val advanced = cache.advanceTo(plan)
        cached = advanced
        return WindowResolution(
            plan = plan,
            window = advanced,
            currentIndexInWindow = plan.currentIndex,
        )
    }

    /**
     * Window decision for *playback drift* (plan P5-C3 prerequisite): as the transport advances on
     * its own, nothing in the app re-plans the window, so a queue longer than the window would stop
     * at the window edge. This is called on every current-item change and returns the cheapest edit
     * that keeps a full look-ahead loaded.
     *
     * Unlike [resolve], an overlapping window shift is reported as [WindowSlide.Incremental] rather
     * than a plan, because rebuilding the transport queue here would audibly restart the song the
     * user is listening to.
     */
    fun resolveDrift(playQueue: PlayQueue): WindowDriftResolution {
        val cache = cached
        val current = playQueue.currentIndex
        if (cache != null && isSafelyInside(cache, current)) {
            return WindowDriftResolution(WindowSlide.None, null, cache, current - cache.startIndex)
        }
        val plan = planner.plan(playQueue)
        if (cache == null) {
            val cold = cache.advanceTo(plan)
            cached = cold
            return WindowDriftResolution(
                slide = WindowSlide.Rebuild,
                plan = plan,
                window = cold,
                currentIndexInWindow = plan.currentIndex,
            )
        }
        val slide = slidePlanner.plan(
            fullOrder = ControllerQueuePlanner.expand(playQueue),
            oldStart = cache.startIndex,
            oldEndExclusive = cache.endIndexExclusive,
            newStart = plan.windowStartIndex,
            newEndExclusive = plan.windowEndIndexExclusive,
        )
        if (slide is WindowSlide.None) {
            // The range did not actually move (e.g. already pinned at the head or tail of the
            // queue); keep the cached window so we do not touch the transport every tick.
            return WindowDriftResolution(WindowSlide.None, null, cache, current - cache.startIndex)
        }
        val advanced = cache.advanceTo(plan)
        cached = advanced
        return WindowDriftResolution(
            slide = slide,
            plan = if (slide is WindowSlide.Rebuild) plan else null,
            window = advanced,
            currentIndexInWindow = plan.currentIndex,
        )
    }

    private fun PlaybackWindowState?.advanceTo(plan: ControllerQueuePlan) = PlaybackWindowState(
        startIndex = plan.windowStartIndex,
        endIndexExclusive = plan.windowEndIndexExclusive,
        mediaIds = plan.mediaItems.map { it.mediaId },
        windowVersion = (this?.windowVersion ?: 0L) + 1L,
    )

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

/**
 * Result of [ControllerWindowSynchronizer.resolveDrift]. [plan] is non-null only when [slide] is
 * [WindowSlide.Rebuild]; for [WindowSlide.Incremental] the caller applies the add/remove edits and
 * lets Media3 keep the current item playing.
 */
data class WindowDriftResolution(
    val slide: WindowSlide,
    val plan: ControllerQueuePlan?,
    val window: PlaybackWindowState,
    val currentIndexInWindow: Int,
)
