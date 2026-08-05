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
        return true
    }

    fun stopInfinitePlay() {
        updateState { it.copy(isInfinitePlay = false, infinitePlayedSongIds = emptySet()) }
        log("stopInfinitePlay")
    }

    fun refillInfinitePlayQueue(startedSongId: Int? = null) {
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

        if (plan.resetHistory) {
            log("refillInfinitePlayQueue: reset played history")
        }

        updateState {
            it.copy(
                playQueue = plan.queue,
                infinitePlayedSongIds = plan.playedSongIds,
            )
        }
        syncPlayerQueue(plan.queue)
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
