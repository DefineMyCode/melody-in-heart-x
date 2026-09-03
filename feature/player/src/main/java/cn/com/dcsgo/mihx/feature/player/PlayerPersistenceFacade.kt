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
        // 诊断：确认 5s autosaver 与事件保存确实在写盘、写入的是否是预期数据。
        log(
            "save playback state: queue=${current.playQueue.songs.size}, index=${current.playQueue.currentIndex}, " +
                "infinite=${current.isInfinitePlay}, position=${positionMs}ms, " +
                "currentSongId=${current.currentSong?.id}"
        )
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
        val result = playbackRestoreCoordinator.restore(state().songs)
        // 诊断：区分「无快照(null)」与「解码出空/少歌队列」——两者在 UI 上都表现为队列空，
        // 但根因完全不同（保存缺失 vs 快照 songId 匹配不上当前曲库）。
        log(
            if (result == null) {
                "restore read: no snapshot to restore"
            } else {
                "restore read: ${result.queue.songs.size} songs, index=${result.queue.currentIndex}, " +
                    "infinite=${result.isInfinitePlay}, playable=${result.playableSession != null}"
            }
        )
        return result
    }

    /**
     * 将恢复结果应用到 UI 状态与控制器队列（应回到主线程调用）
     *
     * @param restoreController 是否重建控制器队列并写入恢复点位置。live session 存在时应传 false：
     *   服务端正在播放，重建队列会覆盖会话、进度回退到快照位置。
     *   但 **UI 侧的队列与歌曲数据仍然必须恢复** —— controller 的 snapshot 同步只调整
     *   `currentIndex`、不会填充 `songs`，若此处一并跳过，UI 播放队列将一直是空的。
     */
    fun applyRestoreResult(
        result: PlaybackRestoreResult,
        restoreController: Boolean = true,
    ) {
        applyPlaybackRestoreResult(result, restoreController)
    }

    private fun applyPlaybackRestoreResult(
        result: PlaybackRestoreResult,
        restoreController: Boolean,
    ) {
        updateState {
            it.copy(
                playQueue = result.queue,
                isInfinitePlay = result.isInfinitePlay,
                infinitePlayedSongIds = result.infinitePlayedSongIds,
            )
        }

        val session = result.playableSession ?: return

        if (!restoreController) {
            // 只补 UI 侧歌曲信息：位置与播放状态留给 connect 后的 syncControllerPlaybackState，
            // 否则会把正在播放的会话覆盖成快照位置，并把 isPlaying 错误地写成 false。
            updateState {
                it.copy(
                    currentSong = session.song,
                    sameNameSongs = session.sameNameSongs,
                )
            }
            log(
                "Playback state restored to UI only (live session active): " +
                    "${result.queue.songs.size} songs, index=${result.queue.currentIndex}, " +
                    "mode=${result.queue.playMode.name}"
            )
            return
        }

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
