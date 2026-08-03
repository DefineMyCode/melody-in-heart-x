package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackState
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer

class PlayerControllerStateFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val synchronizer: ControllerPlaybackStateSynchronizer,
    private val trackedSongId: () -> Int?,
    private val setTrackedSongId: (Int?) -> Unit,
    private val updateDuration: (Int, Long) -> Unit,
    private val startPlayback: (Int, Long) -> Unit,
    private val pausePlaybackTracking: () -> Unit,
    private val resumePlaybackTracking: () -> Unit,
    private val savePlaybackState: () -> Unit,
    private val onIsPlayingChanged: () -> Unit = {},
    private val onControllerPlaybackSynced: (ControllerPlaybackState) -> Unit = {},
) {
    fun syncControllerPlaybackState(snapshot: ControllerPlaybackSnapshot) {
        val result = synchronizer.sync(
            current = state().toControllerPlaybackState(),
            snapshot = snapshot,
            trackedSongId = trackedSongId(),
        )

        result.durationUpdate?.let { update ->
            updateDuration(update.songId, update.durationMs)
        }
        result.playbackStart?.let { start ->
            startPlayback(start.songId, start.durationMs)
        }
        setTrackedSongId(result.trackedSongId)
        updateState { it.applyControllerPlaybackState(result.state) }
        onControllerPlaybackSynced(result.state)
    }

    fun handleControllerIsPlayingChanged(
        isPlaying: Boolean,
        isBuffering: Boolean,
    ) {
        val transition = synchronizer.isPlayingTransition(
            previousIsPlaying = state().isPlaying,
            newIsPlaying = isPlaying,
            isBuffering = isBuffering,
            hasCurrentSong = state().currentSong != null,
        )
        updateState { it.copy(isPlaying = isPlaying) }
        onIsPlayingChanged()

        if (transition.shouldPauseTracking) {
            pausePlaybackTracking()
            savePlaybackState()
        }
        if (transition.shouldResumeTracking) {
            resumePlaybackTracking()
        }
    }
}

private fun PlayerUiState.toControllerPlaybackState(): ControllerPlaybackState {
    return ControllerPlaybackState(
        songs = songs,
        playQueue = playQueue,
        currentSong = currentSong,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        sameNameSongs = sameNameSongs,
    )
}

private fun PlayerUiState.applyControllerPlaybackState(state: ControllerPlaybackState): PlayerUiState {
    return copy(
        playQueue = state.playQueue,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        currentPositionMs = state.currentPositionMs,
        durationMs = state.durationMs,
        sameNameSongs = state.sameNameSongs,
    )
}
