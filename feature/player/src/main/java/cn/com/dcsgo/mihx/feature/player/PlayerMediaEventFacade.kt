package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.QueueManager

class PlayerMediaEventFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val stopPlaybackTracking: () -> Unit,
    private val clearTrackedSong: () -> Unit,
    private val remainingMediaItems: () -> Int,
    private val refillInfinitePlayQueue: (Int?, Boolean) -> Unit,
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val log: (String) -> Unit,
    private val refillThreshold: Int = DEFAULT_REFILL_THRESHOLD,
    private val playOrderBuilder: QueueManager.PlayOrderBuilder = QueueManager.defaultPlayOrderBuilder,
    private val onSleepTimerSongEnded: () -> Unit = {},
) {
    /**
     * 歌曲切换完成时调用（自然结束、手动下一首/上一首、repeat 回绕）。
     *
     * @param startedSongId 新开始歌曲的 ID，用于把业务队列当前项校正到实际播放项
     * @param wrapped       是否发生了窗口尾部 repeat 回绕（上一首位于窗口最后一首、新位置回到窗口开头）。
     *                      回绕说明窗口已耗尽：除了补队列，还会把当前歌曲跳到第一首新补充的歌曲，
     *                      避免回绕后重播旧窗口而不是继续播放新歌曲。
     */
    fun handleMediaItemEnded(startedSongId: Int? = null, wrapped: Boolean = false) {
        stopPlaybackTracking()
        clearTrackedSong()

        val current = state()
        if (current.isInfinitePlay) {
            val remainingSongs = remainingMediaItems()
            if (wrapped || remainingSongs <= refillThreshold) {
                refillInfinitePlayQueue(startedSongId, wrapped)
                log("infinite play refill: remaining=$remainingSongs wrapped=$wrapped")
            }
        }

        restorePlayModeAfterNextSong(startedSongId)

        // 定时关闭「播完最后一曲」：当前歌曲已自然结束，暂停并收尾
        onSleepTimerSongEnded()
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
