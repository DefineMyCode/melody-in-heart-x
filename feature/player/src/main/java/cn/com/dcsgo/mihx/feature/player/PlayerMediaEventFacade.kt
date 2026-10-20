package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicy
import cn.com.dcsgo.mihx.domain.playback.QueueManager

class PlayerMediaEventFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val stopPlaybackTracking: () -> Unit,
    private val clearTrackedSong: () -> Unit,
    private val trackedSongId: () -> Int? = { null },
    private val currentPlayedMs: () -> Long = { 0L },
    private val remainingMediaItems: () -> Int,
    private val refillInfinitePlayQueue: (Int?, Boolean) -> Unit,
    private val refillExplorePlayQueue: (Int?) -> Unit = {},
    /** 探索播放：一首歌结束（切歌/自然结束）时回调收听结果，参数为该歌实际收听毫秒数 */
    private val onExploreSongSettled: (songId: Int, playedMs: Long) -> Unit = { _, _ -> },
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val log: (String) -> Unit,
    private val refillThreshold: Int = DEFAULT_REFILL_THRESHOLD,
    private val exploreRefillThreshold: Int = ExplorePlaybackPolicy.REFILL_THRESHOLD,
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
        // 探索播放：先取「刚结束的歌曲」实际收听时长（stopPlaybackTracking 会清零计时器）
        val endedSongId = trackedSongId()
        val playedMs = currentPlayedMs()
        stopPlaybackTracking()
        clearTrackedSong()

        val current = state()
        // 探索播放：isExplorePlay（会话）与 isExploreModeActive（设置模式）双保险——
        // 恢复/切换时序下可能短暂出现 isExplorePlay 为 true 但模式已关闭，此时不学习、不补队列。
        if (current.isExplorePlay && current.isExploreModeActive) {
            // 收听结果学习：<30s 或 <1/4 总时长 → 不喜欢票，否则喜欢票
            if (endedSongId != null) {
                onExploreSongSettled(endedSongId, playedMs)
            }
            // 队列自动补充：仅当「刚结束」的歌曲是倒数第二首（切歌后新当前曲目剩余 0 首）时，播完后补 5 首。
            // 剩余 1 首 = 结束的是倒数第三首，过早补充且会因业务队列 currentIndex 未同步而回拉重播（2026-10-20 修复）。
            val remainingSongs = remainingMediaItems()
            if (remainingSongs <= exploreRefillThreshold) {
                refillExplorePlayQueue(startedSongId)
                log("explore play refill: remaining=$remainingSongs")
            }
        }
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
