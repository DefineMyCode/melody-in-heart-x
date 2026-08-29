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
    private val startPlayback: (Int, Long, Long) -> Unit,
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
            // 杀进程恢复播放时 snapshot 携带恢复点进度：作为已播时长基数计入统计，
            // 避免"被杀前已播 + 恢复后听完"却因计时器归零不满足 90% 阈值。
            // 正常切歌时新歌位置为 0，不受影响。
            startPlayback(start.songId, start.durationMs, snapshot.currentPositionMs)
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
        // 播放位置不再随每个 controller snapshot 刷新到 uiState（改由 positionMs 窄流驱动），
        // 这里保留离散事件写入的值；播放中 ControllerPlaybackState 其余字段不变时整个副本相等，
        // StateFlow 不会发出新值，避免整壳重组。
        currentPositionMs = currentPositionMs,
        durationMs = state.durationMs,
        sameNameSongs = state.sameNameSongs,
    )
}
