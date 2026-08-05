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
    fun savePlaybackStateAsync(positionMs: Long = currentPlaybackPositionMs()) {
        // 先捕获位置，再把 DataStore 写移到 IO，避免播放中主线程每秒阻塞写盘
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

    /** 仅在 IO 线程读取并解码播放状态，返回 null 表示无需恢复；由调用方在合适线程 [applyRestoreResult] */
    fun restorePlaybackState(): PlaybackRestoreResult? {
        return playbackRestoreCoordinator.restore(state().songs)
    }

    /** 将恢复结果应用到 UI 状态与控制器队列（应回到主线程调用） */
    fun applyRestoreResult(result: PlaybackRestoreResult) {
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
