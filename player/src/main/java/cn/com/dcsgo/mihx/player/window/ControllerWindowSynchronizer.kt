package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan

class ControllerWindowSynchronizer(
    private val planner: PlaybackWindowPlanner = PlaybackWindowPlanner(),
) {
    private var lastWindow: PlaybackWindowState? = null

    fun planIfNeeded(queue: PlayQueue): PlaybackWindowState? {
        val currentSong = queue.currentSong
        val existing = lastWindow
        if (existing != null && currentSong != null && existing.songs.any { it.id == currentSong.id }) {
            return null
        }

        return planner.plan(queue)?.also { lastWindow = it }
    }

    fun planControllerQueue(
        queue: PlayQueue,
        force: Boolean = true,
    ): ControllerQueuePlan? {
        if (force) {
            invalidate()
        }
        val window = planIfNeeded(queue) ?: lastWindow ?: return null
        val currentWindowIndex = queue.currentSong
            ?.let { current -> window.songs.indexOfFirst { it.id == current.id } }
            ?.takeIf { it >= 0 }
            ?: window.controllerStartIndex
        return ControllerQueuePlan(
            songs = window.songs,
            startIndex = currentWindowIndex,
        )
    }

    fun invalidate() {
        lastWindow = null
    }
}
