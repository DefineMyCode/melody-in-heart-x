package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.core.model.WordPreferenceKind
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicy
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
    private val rawPlayCounts: suspend (List<Int>) -> Map<Int, Int> = { emptyMap() },
    private val log: (String) -> Unit,
    private val planner: RandomQueuePlanner = RandomQueuePlanner(),
    // 情境化随心播放（一期）：每次随机时即时判定当前生效时段。
    // null = 未生效（开关关 / 无配置 / 无命中时段），随机行为与现状完全一致。
    private val moodSlotResolver: MoodSlotResolver = MoodSlotResolver(),
    private val moodSlotConfigs: () -> List<cn.com.dcsgo.mihx.core.model.TimeSlotConfig> = { emptyList() },
    private val moodSlotEnabled: suspend () -> Boolean = { false },
    private val moodEmotionTagsOf: (Song) -> List<String> = { emptyList() },
    // 探索播放（2026-10-20）：模式开关 + 情绪词条收听偏好（计票制）
    private val exploreModeEnabled: suspend () -> Boolean = { false },
    private val wordPreferenceOf: (String) -> WordPreference = {
        WordPreference(word = it, likeVotes = 0, dislikeVotes = 0)
    },
    // 当前时刻（当日分钟数）；注入以便单测脱离真实时钟
    private val nowMinuteOfDay: () -> Int = {
        java.util.Calendar.getInstance().let { calendar ->
            calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        }
    },
) {
    private val recentPlayedSongIds = mutableSetOf<Int>()

    /** 当前是否处于探索播放模式（设置里选了「探索模式」） */
    internal suspend fun exploreActive(): Boolean = exploreModeEnabled()

    /** 暴露当前生效的情境时段（供归因；与 playRandomQueue 内部判定同源） */
    internal suspend fun currentMoodSlot(): MoodSlotState? = activeMoodSlot()

    /** 当前生效的情境时段；null = 增强未生效 */
    private suspend fun activeMoodSlot(): MoodSlotState? {
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
     * 生成随机队列并开始顺序播放（M-5：suspend，rawPlayCounts 挂起查库，调用方负责调度）。
     * 探索播放（2026-10-20）：探索模式下从全库随机 [ExplorePlaybackPolicy.INITIAL_BATCH_SIZE]=10 首，
     * 并置 isExplorePlay（无限随机播放被禁用、队列剩 1 首时按喜欢/不喜欢词条补 5 首）。
     * @return true 表示已生成队列并开始播放；false 表示库中无可播放歌曲，未开始播放。
     */
    suspend fun playRandomQueue(): Boolean {
        val current = state()
        val explore = exploreModeEnabled()
        val mood = if (explore) null else activeMoodSlot()
        val candidates = mood?.let { filterByMoodTags(current.songs, it) } ?: current.songs
        // 探索播放：固定生成 10 首；非探索沿用 planner 默认批量（生产 20 首）
        val plan = if (explore) {
            planner.planRandomQueue(
                songs = candidates,
                recentSongIds = recentPlayedSongIds,
                uniformRandomEnabled = current.globalUniformRandomEnabled,
                playCounts = rawPlayCounts(candidates.map { it.id }),
                batchSize = ExplorePlaybackPolicy.INITIAL_BATCH_SIZE,
            )
        } else {
            planner.planRandomQueue(
                songs = candidates,
                recentSongIds = recentPlayedSongIds,
                uniformRandomEnabled = current.globalUniformRandomEnabled,
                playCounts = rawPlayCounts(candidates.map { it.id }),
            )
        } ?: return false

        setPlayQueue(plan.songs, 0, PlayMode.SEQUENTIAL)
        updateState {
            it.copy(
                isInfinitePlay = false,
                infinitePlayedSongIds = emptySet(),
                isExplorePlay = explore,
            )
        }
        recentPlayedSongIds.clear()
        recentPlayedSongIds.addAll(plan.recentSongIds)
        if (plan.resetHistory) {
            log("playRandomQueue: reset recent history")
        }

        log(
            "playRandomQueue: songs=${plan.songs.size}, recent=${recentPlayedSongIds.size}, " +
                "mood=${mood?.slotName}, explore=$explore"
        )
        return true
    }

    /**
     * 开启无限随机播放。
     * @return true 表示已开启；false 表示库中无可播放歌曲，未开启。
     */
    suspend fun startInfinitePlay(): Boolean {
        val current = state()
        // 探索播放（2026-10-20）：仅当「探索会话」进行中（播放探索队列）时无限随机被禁用；
        // 用户手动点歌退出探索后（isExplorePlay=false），即使设置仍是探索模式也可开启无限随机。
        if (current.isExplorePlay) {
            log("startInfinitePlay: blocked by explore session")
            return false
        }
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
    suspend fun refillInfinitePlayQueue(startedSongId: Int? = null, advanceAfterWrap: Boolean = false) {
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

    /**
     * 探索播放补队列（2026-10-20）：当前歌曲是倒数第二首、播完后调用。
     * 候选池按情绪词条收听偏好筛选：优先「含 ≥1 喜欢词且不含不喜欢词」；
     * 池空回退「仅排除不喜欢词」；再空回退全库。每次补 [ExplorePlaybackPolicy.REFILL_COUNT]=5 首。
     *
     * @param startedSongId 刚切到的新歌曲 ID：切歌后业务队列 currentIndex 尚未随控制器同步，
     *                      用它校正后再追加/同步，否则会回拉到旧歌重新播放（2026-10-20 修复）。
     */
    suspend fun refillExplorePlayQueue(startedSongId: Int? = null) {
        val current = state()
        if (!current.isExplorePlay) return

        val pool = buildExploreRefillPool(current.songs)
        val queue = current.playQueue.withCurrentSongId(startedSongId)
        val plan = planner.planExploreRefill(
            allSongs = pool,
            queue = queue,
            count = ExplorePlaybackPolicy.REFILL_COUNT,
        ) ?: return
        if (plan.addedSongs.isEmpty()) {
            log("refillExplorePlayQueue: no more songs outside queue")
            return
        }

        updateState { it.copy(playQueue = plan.queue) }
        syncPlayerQueue(plan.queue)
        log("refillExplorePlayQueue: added=${plan.addedSongs.size} (queue=${queue.songs.size} -> ${plan.queue.songs.size})")
    }

    /** 退出探索播放会话（切换模式 / 关闭探索时由调用方调用） */
    fun stopExplorePlay() {
        updateState { it.copy(isExplorePlay = false) }
        log("stopExplorePlay")
    }

    /**
     * 构建探索播放的补充候选池（按喜欢/不喜欢词条分层降级）：
     * 1. 含 ≥1 喜欢词 且 不含不喜欢词；
     * 2. 仅不含不喜欢词；
     * 3. 全库。
     * 词条来自歌曲情绪展示词条（用户校准优先）+ 计票归属（票多者胜）。
     */
    private fun buildExploreRefillPool(songs: List<Song>): List<Song> {
        val likedWords = mutableSetOf<String>()
        val dislikedWords = mutableSetOf<String>()
        val neutralWords = mutableSetOf<String>()
        (songs.flatMap { moodEmotionTagsOf(it) }).distinct().forEach { word ->
            when (wordPreferenceOf(word).kind) {
                WordPreferenceKind.LIKE -> likedWords += word
                WordPreferenceKind.DISLIKE -> dislikedWords += word
                WordPreferenceKind.NEUTRAL -> neutralWords += word
            }
        }
        log(
            "explore refill: liked=[${likedWords.sorted().joinToString(",")}] " +
                "disliked=[${dislikedWords.sorted().joinToString(",")}] neutral=[${neutralWords.sorted().joinToString(",")}]"
        )
        if (likedWords.isEmpty() && dislikedWords.isEmpty()) {
            log("explore refill: no like/dislike words learned yet, use full library")
            return songs
        }

        val likedAndNoDislike = songs.filter { song ->
            val tags = moodEmotionTagsOf(song)
            tags.any { it in likedWords } && tags.none { it in dislikedWords }
        }
        if (likedAndNoDislike.isNotEmpty()) {
            log("explore refill: pool=${likedAndNoDislike.size} (liked & no-dislike)")
            return likedAndNoDislike
        }

        val noDislike = songs.filter { song ->
            moodEmotionTagsOf(song).none { it in dislikedWords }
        }
        if (noDislike.isNotEmpty()) {
            log("explore refill: pool=${noDislike.size} (no-dislike only, no liked match)")
            return noDislike
        }

        log("explore refill: fall back to full library")
        return songs
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
