package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import cn.com.dcsgo.mihx.domain.playback.MoodSlotPolicy
import cn.com.dcsgo.mihx.domain.playback.MoodSlotResolver
import cn.com.dcsgo.mihx.domain.playback.RandomQueuePlanner

/**
 * 情境化随心播放的判定结果（设计文档 §4.2/§4.3）：
 * [tags] 为 null 表示增强未生效；非 null 时随机只从带这些词条的歌曲中选。
 * [slotName] 用于 toast 归因。
 */
data class MoodSlotState(
    val slotName: String,
    val tags: Set<String>,
)

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
    // 情境化随心播放（一期）：每次随机时即时判定当前生效时段。
    // null = 未生效（开关关 / 无配置 / 无命中时段），随机行为与现状完全一致。
    private val moodSlotResolver: MoodSlotResolver = MoodSlotResolver(),
    private val moodSlotConfigs: () -> List<cn.com.dcsgo.mihx.core.model.TimeSlotConfig> = { emptyList() },
    private val moodSlotEnabled: () -> Boolean = { false },
    private val moodEmotionTagsOf: (Song) -> List<String> = { emptyList() },
    // 当前时刻（当日分钟数）；注入以便单测脱离真实时钟
    private val nowMinuteOfDay: () -> Int = {
        java.util.Calendar.getInstance().let { calendar ->
            calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        }
    },
) {
    private val recentPlayedSongIds = mutableSetOf<Int>()

    /** 暴露当前生效的情境时段（供 UI 归因 toast；与 playRandomQueue 内部判定同源） */
    internal fun currentMoodSlot(): MoodSlotState? = activeMoodSlot()

    /** 当前生效的情境时段；null = 增强未生效 */
    private fun activeMoodSlot(): MoodSlotState? {
        if (!moodSlotEnabled()) return null
        val hit = moodSlotResolver.hitSlot(moodSlotConfigs(), nowMinuteOfDay()) ?: return null
        return MoodSlotState(slotName = hit.name, tags = hit.tags.toSet())
    }

    /**
     * 按生效时段的情绪词条过滤候选池（§4.2）。
     * 过滤发生在 planner 入口：分层抢占（低播放优先）与最近播放淘汰零改动复用。
     * 词条组合 0 首时返回 null → 调用方回退全库随机（§4.3 降级）。
     */
    private fun filterByMoodTags(
        songs: List<Song>,
        mood: MoodSlotState,
    ): List<Song>? {
        val filtered = songs.filter { song ->
            moodEmotionTagsOf(song).any { it in mood.tags }
        }
        if (filtered.isEmpty()) {
            log("moodSlot: no songs for tags=${mood.tags}, fall back to full library")
            return null
        }
        if (filtered.size < MoodSlotPolicy.POOL_WARN_THRESHOLD) {
            log("moodSlot: small pool (${filtered.size}) for tags=${mood.tags}, will loop")
        }
        return filtered
    }

    /**
     * 生成随机队列并开始顺序播放。
     * @return true 表示已生成队列并开始播放；false 表示库中无可播放歌曲，未开始播放。
     */
    fun playRandomQueue(): Boolean {
        val mood = activeMoodSlot()
        val candidates = mood?.let { filterByMoodTags(state().songs, it) } ?: state().songs
        val plan = planner.planRandomQueue(
            songs = candidates,
            recentSongIds = recentPlayedSongIds,
            uniformRandomEnabled = state().globalUniformRandomEnabled,
            playCounts = rawPlayCounts(candidates.map { it.id }),
        ) ?: return false

        updateState { it.copy(isInfinitePlay = false, infinitePlayedSongIds = emptySet()) }
        recentPlayedSongIds.clear()
        recentPlayedSongIds.addAll(plan.recentSongIds)
        if (plan.resetHistory) {
            log("playRandomQueue: reset recent history")
        }

        setPlayQueue(plan.songs, 0, PlayMode.SEQUENTIAL)
        log("playRandomQueue: songs=${plan.songs.size}, recent=${recentPlayedSongIds.size}, mood=${mood?.slotName}")
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

        val mood = activeMoodSlot()
        val candidates = mood?.let { filterByMoodTags(current.songs, it) } ?: current.songs
        val queue = current.playQueue.withCurrentSongId(startedSongId)
        val plan = planner.planInfiniteRefill(
            allSongs = candidates,
            queue = queue,
            playedSongIds = current.infinitePlayedSongIds,
            uniformRandomEnabled = current.globalUniformRandomEnabled,
            playCounts = rawPlayCounts(candidates.map { it.id }),
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
