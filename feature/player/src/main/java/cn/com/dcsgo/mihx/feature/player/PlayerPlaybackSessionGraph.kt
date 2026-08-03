package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationTracker
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionController
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionCoordinator
import cn.com.dcsgo.mihx.domain.playback.SeekCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PlayerPlaybackSessionGraph"

internal class PlayerPlaybackSessionGraph(
    private val scope: CoroutineScope,
    controller: PlaybackSessionController,
    durationTracker: PlaybackDurationTracker,
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val setTrackedSongId: (Int) -> Unit,
    private val updateSameNameSongs: (Song) -> Unit,
    private val savePlaybackState: () -> Unit,
    private val onPlaybackStateChanged: () -> Unit = {},
    private val planControllerQueue: (PlayQueue, Int) -> ControllerQueuePlan?,
) {
    private val sessionCoordinator = PlaybackSessionCoordinator(
        controller = controller,
        durationTracker = durationTracker,
        planControllerQueue = planControllerQueue,
    )
    private val seekCoordinator = SeekCoordinator(
        session = sessionCoordinator,
        controllerIsPlaying = { controller.isPlaying },
        currentIsPlaying = { state().isPlaying },
    )
    private val sessionFacade = PlayerSessionFacade(
        state = state,
        updateState = updateState,
        sessionCoordinator = sessionCoordinator,
        seekCoordinator = seekCoordinator,
        setTrackedSongId = setTrackedSongId,
        updateSameNameSongs = updateSameNameSongs,
        savePlaybackState = savePlaybackState,
        onPlaybackStateChanged = onPlaybackStateChanged,
        scheduleAfterSeek = { task ->
            scope.launch {
                delay(500)
                task()
            }
        },
        log = { message -> AppLog.debug(TAG, message) },
    )

    fun pausePlayback() {
        sessionFacade.pausePlayback()
    }

    fun resumePlayback() {
        sessionFacade.resumePlayback()
    }

    fun currentPlaybackPositionMs(): Long {
        return sessionFacade.currentPlaybackPositionMs()
    }

    fun startQueuePlayback(queue: PlayQueue, index: Int): Boolean {
        return sessionFacade.startQueuePlayback(queue, index)
    }

    fun prepareQueue(queue: PlayQueue, index: Int, positionMs: Long): Boolean {
        return sessionFacade.prepareQueue(queue, index, positionMs)
    }

    fun startSinglePlayback(song: Song): Boolean {
        return sessionFacade.startSinglePlayback(song)
    }

    fun startSeeking() {
        sessionFacade.startSeeking()
    }

    fun endSeeking(positionMs: Long) {
        sessionFacade.endSeeking(positionMs)
    }

    fun seekTo(positionMs: Long) {
        sessionFacade.seekTo(positionMs)
    }
}
