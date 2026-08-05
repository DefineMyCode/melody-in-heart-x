package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.RandomQueuePlanner

class PlayerRandomQueueFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val setPlayQueue: (List<Song>, Int, PlayMode) -> Unit,
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val remainingMediaItems: () -> Int,
    private val playFromQueue: (PlayQueue, Int) -> Unit,
    private val rawPlayCounts: (List<Int>) -> Map<Int, Int> = { emptyMap() },
    private val log: (String) -> Unit,
    private val planner: RandomQueuePlanner = RandomQueuePlanner(),
) {
    private val recentPlayedSongIds = mutableSetOf<Int>()

    /**
     * 生成随机队列并开始顺序播放。
     * @return true 表示已生成队列并开始播放；false 表示库中无可播放歌曲，未开始播放。
     */
    fun playRandomQueue(): Boolean {
        val plan = planner.planRandomQueue(
            songs = state().songs,
            recentSongIds = recentPlayedSongIds,
            uniformRandomEnabled = state().globalUniformRandomEnabled,
            playCounts = rawPlayCounts(state().songs.map { it.id }),
        ) ?: return false

        updateState { it.copy(isInfinitePlay = false, infinitePlayedSongIds = emptySet()) }
        recentPlayedSongIds.clear()
        recentPlayedSongIds.addAll(plan.recentSongIds)
        if (plan.resetHistory) {
            log("playRandomQueue: reset recent history")
        }

        setPlayQueue(plan.songs, 0, PlayMode.SEQUENTIAL)
        log("playRandomQueue: songs=${plan.songs.size}, recent=${recentPlayedSongIds.size}")
        return true
    }

    /**
     * 开启无限随机播放。
     * @return true 表示已开启；false 表示库中无可播放歌曲，未开启。
     */
    fun startInfinitePlay(): Boolean {
        val current = state()
        val plan = planner.planInfiniteStart(
            allSongs = current.songs,
            queue = current.playQueue,
        ) ?: return false

        updateState {
            it.copy(
                isInfinitePlay = true,
                infinitePlayedSongIds = plan.playedSongIds,
            )
        }
        log("startInfinitePlay: keep current queue, covered=${plan.playedSongIds.size}")

        // 开启时若已处于队列尾部（剩余不足补队列阈值），立即补队列。
        // 否则当前歌曲 A 仍是窗口最后一首：自然结束或系统媒体键（耳机/锁屏/通知栏）切下一首时，
        // Media3 会因 REPEAT_MODE_ALL 回绕到旧窗口第一首 B，而新歌曲补在 B 之后，
        // 导致用户重播旧窗口而不是继续播放新补充的歌曲（播放页下一首按钮是切歌前先补队列，不受影响）。
        if (remainingMediaItems() <= PlayerMediaEventFacade.DEFAULT_REFILL_THRESHOLD) {
            refillInfinitePlayQueue(startedSongId = current.currentSong?.id)
            log("startInfinitePlay: refilled at queue tail")
        }
        return true
    }

    fun stopInfinitePlay() {
        updateState { it.copy(isInfinitePlay = false, infinitePlayedSongIds = emptySet()) }
        log("stopInfinitePlay")
    }

    /**
     * 无限播放补队列：向队尾追加尚未覆盖的可播放歌曲。
     *
     * @param startedSongId    用于把业务队列当前项校正到实际播放项
     * @param advanceAfterWrap 窗口尾部回绕后调用：把当前歌曲跳到第一首新补充的歌曲并从 0 开始播放。
     *                         否则保持当前歌曲不变，仅扩展队列（播放页下一首按钮的切歌前补队列路径）。
     */
    fun refillInfinitePlayQueue(startedSongId: Int? = null, advanceAfterWrap: Boolean = false) {
        val current = state()
        if (!current.isInfinitePlay) return

        val queue = current.playQueue.withCurrentSongId(startedSongId)
        val plan = planner.planInfiniteRefill(
            allSongs = current.songs,
            queue = queue,
            playedSongIds = current.infinitePlayedSongIds,
            uniformRandomEnabled = current.globalUniformRandomEnabled,
            playCounts = rawPlayCounts(current.songs.map { it.id }),
        ) ?: return
        if (plan.addedSongs.isEmpty()) return

        if (plan.resetHistory) {
            log("refillInfinitePlayQueue: reset played history")
        }

        if (advanceAfterWrap) {
            // 回绕后跳到第一首新补充的歌曲：追加位于原队列末尾之后，把当前项指到那里并从 0 开始播放。
            val firstNewIndex = queue.songs.size
            val newQueue = plan.queue.withCurrentIndex(firstNewIndex)
            updateState {
                it.copy(
                    playQueue = newQueue,
                    infinitePlayedSongIds = plan.playedSongIds,
                )
            }
            playFromQueue(newQueue, firstNewIndex)
        } else {
            updateState {
                it.copy(
                    playQueue = plan.queue,
                    infinitePlayedSongIds = plan.playedSongIds,
                )
            }
            syncPlayerQueue(plan.queue)
        }
        log("refillInfinitePlayQueue: added=${plan.addedSongs.size}")
    }

    private fun PlayQueue.withCurrentSongId(songId: Int?): PlayQueue {
        if (songId == null) return this
        val index = songs.indexOfFirst { it.id == songId }
        return if (index >= 0 && index != currentIndex) {
            copy(currentIndex = index)
        } else {
            this
        }
    }
}
