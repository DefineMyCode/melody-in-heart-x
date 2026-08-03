package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlanner

class PlaybackWindowPlanner(
    private val previousCount: Int = DEFAULT_PREVIOUS_COUNT,
    private val nextCount: Int = DEFAULT_NEXT_COUNT,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) {
    fun plan(queue: PlayQueue): PlaybackWindowState? {
        val controllerQueue = ControllerQueuePlanner.plan(queue, isPlayable = isPlayable) ?: return null

        val start = (controllerQueue.startIndex - previousCount).coerceAtLeast(0)
        val endExclusive = (controllerQueue.startIndex + nextCount + 1)
            .coerceAtMost(controllerQueue.songs.size)
        return PlaybackWindowState(
            songs = controllerQueue.songs.subList(start, endExclusive),
            controllerStartIndex = controllerQueue.startIndex - start,
            fullQueueStartIndex = start,
            fullQueueCurrentIndex = queue.currentIndex,
        )
    }

    companion object {
        const val DEFAULT_PREVIOUS_COUNT = 20
        const val DEFAULT_NEXT_COUNT = 50
    }
}
