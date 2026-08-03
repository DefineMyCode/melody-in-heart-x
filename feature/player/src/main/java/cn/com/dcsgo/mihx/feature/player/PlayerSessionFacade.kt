package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionCoordinator
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionResult
import cn.com.dcsgo.mihx.domain.playback.SeekCoordinator
import cn.com.dcsgo.mihx.domain.playback.SeekResult

class PlayerSessionFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val sessionCoordinator: PlaybackSessionCoordinator,
    private val seekCoordinator: SeekCoordinator,
    private val setTrackedSongId: (Int) -> Unit,
    private val updateSameNameSongs: (Song) -> Unit,
    private val savePlaybackState: () -> Unit,
    private val onPlaybackStateChanged: () -> Unit = {},
    private val scheduleAfterSeek: (() -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    fun pausePlayback() {
        val result = sessionCoordinator.pause(state().isPlaying)
        applyPlaybackSessionResult(result)
        if (result.shouldSavePlaybackState) {
            savePlaybackState()
        }
    }

    fun resumePlayback() {
        val current = state()
        applyPlaybackSessionResult(
            sessionCoordinator.resume(
                currentIsPlaying = current.isPlaying,
                hasCurrentSong = current.currentSong != null,
            )
        )
    }

    fun currentPlaybackPositionMs(): Long {
        return sessionCoordinator.currentPlaybackPositionMs(state().currentPositionMs)
    }

    fun startQueuePlayback(queue: PlayQueue, index: Int): Boolean {
        val result = sessionCoordinator.startQueuePlayback(queue, index) ?: return false
        applyPlaybackSessionResult(result)
        return true
    }

    fun prepareQueue(queue: PlayQueue, index: Int, positionMs: Long): Boolean {
        return sessionCoordinator.prepareQueue(queue, index, positionMs)
    }

    fun startSinglePlayback(song: Song): Boolean {
        val result = sessionCoordinator.startSinglePlayback(song) ?: return false
        applyPlaybackSessionResult(result)
        return true
    }

    fun startSeeking() {
        seekCoordinator.startSeeking()
    }

    fun endSeeking(positionMs: Long) {
        applySeekResult(seekCoordinator.endSeeking(positionMs))
    }

    fun seekTo(positionMs: Long) {
        applySeekResult(seekCoordinator.seekTo(positionMs))
    }

    private fun applyPlaybackSessionResult(result: PlaybackSessionResult) {
        result.trackedSongId?.let(setTrackedSongId)
        result.sameNameSong?.let(updateSameNameSongs)
        result.stateUpdate?.let { update ->
            updateState { current ->
                current.copy(
                    currentSong = update.currentSong ?: current.currentSong,
                    isPlaying = update.isPlaying ?: current.isPlaying,
                    currentPositionMs = update.currentPositionMs ?: current.currentPositionMs,
                    durationMs = update.durationMs ?: current.durationMs,
                )
            }
            if (update.isPlaying != null) {
                onPlaybackStateChanged()
            }
        }
    }

    private fun applySeekResult(result: SeekResult) {
        updateState { it.copy(currentPositionMs = result.positionMs) }
        if (result.shouldSyncIsPlaying) {
            syncIsPlayingAfterSeek()
        }
    }

    private fun syncIsPlayingAfterSeek() {
        scheduleAfterSeek {
            val actualIsPlaying = seekCoordinator.syncedIsPlayingAfterSeek()
            if (actualIsPlaying != null) {
                log("syncIsPlayingAfterSeek: fixing isPlaying ${state().isPlaying} -> $actualIsPlaying")
                updateState { it.copy(isPlaying = actualIsPlaying) }
            }
        }
    }
}
