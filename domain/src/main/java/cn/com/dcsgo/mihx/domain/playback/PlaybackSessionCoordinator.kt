package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

interface PlaybackSessionController {
    val hasCurrentMediaItem: Boolean
    val isPlaying: Boolean?

    fun playQueue(plan: ControllerQueuePlan)
    fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long)
    fun playSingle(song: Song): Boolean
    fun pause()
    fun play()
    fun seekTo(positionMs: Long)
    fun currentPositionMs(fallback: Long = 0L): Long
    fun durationMs(fallback: Long = 0L): Long
}

interface PlaybackDurationTracker {
    fun startPlayback(songId: Int, durationMs: Long)
    fun pausePlayback()
    fun resumePlayback()
    fun startSeeking()
    fun endSeeking()
}

interface PlaybackDurationMonitor : PlaybackDurationTracker {
    fun updateDuration(songId: Int, durationMs: Long)
    fun stopPlayback()
    fun release()
}

fun interface PlaybackDurationMonitorFactory {
    fun create(): PlaybackDurationMonitor
}

data class PlaybackSessionStateUpdate(
    val currentSong: Song? = null,
    val isPlaying: Boolean? = null,
    val currentPositionMs: Long? = null,
    val durationMs: Long? = null,
)

data class PlaybackSessionResult(
    val stateUpdate: PlaybackSessionStateUpdate? = null,
    val trackedSongId: Int? = null,
    val sameNameSong: Song? = null,
    val shouldSavePlaybackState: Boolean = false,
)

class PlaybackSessionCoordinator(
    private val controller: PlaybackSessionController,
    private val durationTracker: PlaybackDurationTracker,
    private val planControllerQueue: (PlayQueue, Int) -> ControllerQueuePlan?,
) : SeekPlaybackSession {
    fun pause(currentIsPlaying: Boolean): PlaybackSessionResult {
        controller.pause()
        if (!currentIsPlaying) return PlaybackSessionResult()

        durationTracker.pausePlayback()
        return PlaybackSessionResult(
            stateUpdate = PlaybackSessionStateUpdate(isPlaying = false),
            shouldSavePlaybackState = true,
        )
    }

    fun resume(currentIsPlaying: Boolean, hasCurrentSong: Boolean): PlaybackSessionResult {
        controller.play()
        if (currentIsPlaying || !hasCurrentSong) return PlaybackSessionResult()

        durationTracker.resumePlayback()
        return PlaybackSessionResult(
            stateUpdate = PlaybackSessionStateUpdate(isPlaying = true),
        )
    }

    fun currentPlaybackPositionMs(fallback: Long): Long {
        return controller.currentPositionMs(fallback)
    }

    override fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
    }

    override fun startSeeking() {
        durationTracker.startSeeking()
    }

    override fun endSeeking() {
        durationTracker.endSeeking()
    }

    fun startQueuePlayback(queue: PlayQueue, index: Int): PlaybackSessionResult? {
        val controllerQueue = planControllerQueue(queue, index) ?: return null
        val song = queue.songs.getOrNull(index) ?: return null
        controller.playQueue(controllerQueue)
        val duration = controller.durationMs()
        durationTracker.startPlayback(song.id, duration)
        return PlaybackSessionResult(
            stateUpdate = playingState(song, duration),
            trackedSongId = song.id,
            sameNameSong = song,
        )
    }

    fun prepareQueue(queue: PlayQueue, index: Int, positionMs: Long): Boolean {
        val controllerQueue = planControllerQueue(queue, index) ?: return false
        controller.prepareQueue(controllerQueue, positionMs)
        return true
    }

    fun startSinglePlayback(song: Song): PlaybackSessionResult? {
        if (!controller.playSingle(song)) return null
        val duration = controller.durationMs()
        durationTracker.startPlayback(song.id, duration)
        return PlaybackSessionResult(
            stateUpdate = playingState(song, duration),
            trackedSongId = song.id,
            sameNameSong = song,
        )
    }

    private fun playingState(song: Song, duration: Long): PlaybackSessionStateUpdate {
        return PlaybackSessionStateUpdate(
            currentSong = song,
            isPlaying = true,
            currentPositionMs = 0L,
            durationMs = duration,
        )
    }
}
