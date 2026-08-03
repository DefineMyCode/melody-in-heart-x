package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.QueueManager

class PlayerMediaEventFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val stopPlaybackTracking: () -> Unit,
    private val clearTrackedSong: () -> Unit,
    private val remainingMediaItems: () -> Int,
    private val refillInfinitePlayQueue: (Int?) -> Unit,
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val log: (String) -> Unit,
    private val refillThreshold: Int = DEFAULT_REFILL_THRESHOLD,
    private val playOrderBuilder: QueueManager.PlayOrderBuilder = QueueManager.defaultPlayOrderBuilder,
) {
    fun handleMediaItemEnded(startedSongId: Int? = null) {
        stopPlaybackTracking()
        clearTrackedSong()

        val current = state()
        if (current.isInfinitePlay) {
            val remainingSongs = remainingMediaItems()
            if (remainingSongs <= refillThreshold) {
                refillInfinitePlayQueue(startedSongId)
                log("infinite play refill: remaining=$remainingSongs")
            }
        }

        restorePlayModeAfterNextSong(startedSongId)
    }

    fun handlePlaybackEnded() {
        updateState { it.copy(isPlaying = false, currentPositionMs = 0L) }
    }

    private fun restorePlayModeAfterNextSong(startedSongId: Int?) {
        val current = state()
        val plan = QueueManager.restorePlayModeAfterNextSong(
            queue = current.playQueue,
            currentSongId = current.currentSong?.id,
            nextPlaySongId = current.nextPlaySongId,
            playModeBeforeNext = current.playModeBeforeNext,
            startedSongId = startedSongId,
            playOrderBuilder = playOrderBuilder,
        ) ?: return

        updateState {
            it.copy(
                playQueue = plan.queue,
                nextPlaySongId = null,
                playModeBeforeNext = null,
            )
        }
        syncPlayerQueue(plan.queue)
        log("maybeRestorePlayModeAfterNextSong: restored play mode to ${current.playModeBeforeNext}")
    }

    companion object {
        const val DEFAULT_REFILL_THRESHOLD = 5
    }
}
