package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreResult
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestorer
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorage

class PlayerPersistenceFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val playbackStateStore: PlaybackStateStorage,
    private val playbackRestoreCoordinator: PlaybackRestorer,
    private val currentPlaybackPositionMs: () -> Long,
    private val prepareControllerQueue: (PlayQueue, Int, Long) -> Boolean,
    private val launchIo: (() -> Unit) -> Unit,
    private val log: (String) -> Unit,
) {
    fun savePlaybackStateAsync() {
        val positionMs = currentPlaybackPositionMs()
        launchIo {
            savePlaybackState(positionMs)
        }
    }

    fun savePlaybackState(positionMs: Long = currentPlaybackPositionMs()) {
        val current = state()
        playbackStateStore.save(
            queue = current.playQueue,
            positionMs = positionMs,
            isInfinitePlay = current.isInfinitePlay,
            infinitePlayedSongIds = current.infinitePlayedSongIds,
            currentSongId = current.currentSong?.id,
        )
    }

    fun clearPlaybackState() {
        playbackStateStore.clear()
    }

    fun restorePlaybackState() {
        val result = playbackRestoreCoordinator.restore(state().songs) ?: return
        applyPlaybackRestoreResult(result)
    }

    private fun applyPlaybackRestoreResult(result: PlaybackRestoreResult) {
        updateState {
            it.copy(
                playQueue = result.queue,
                isInfinitePlay = result.isInfinitePlay,
                infinitePlayedSongIds = result.infinitePlayedSongIds,
            )
        }

        val session = result.playableSession ?: return
        prepareControllerQueue(result.queue, result.queue.currentIndex, session.positionMs)
        updateState {
            it.copy(
                currentSong = session.song,
                currentPositionMs = session.positionMs,
                isPlaying = false,
                sameNameSongs = session.sameNameSongs,
            )
        }
        log(
            "Playback state restored: ${result.queue.songs.size} songs, " +
                "index=${result.queue.currentIndex}, " +
                "position=${session.positionMs}ms, mode=${result.queue.playMode.name}"
        )
    }
}
