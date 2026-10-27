package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.RandomQueuePlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRandomQueueFacadeTest {

    private var state = PlayerUiState()
    private var setQueueSongs: List<Song>? = null
    private var setQueueMode: PlayMode? = null
    private var syncedQueue: PlayQueue? = null
    private var remainingItems = Int.MAX_VALUE
    private var playedFromQueue: PlayQueue? = null
    private var playedFromIndex: Int? = null
    private val logs = mutableListOf<String>()
    private val facade = PlayerRandomQueueFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        setPlayQueue = { songs, startIndex, mode ->
            state = state.copy(playQueue = PlayQueue().setQueue(songs, startIndex, mode))
            setQueueSongs = songs
            setQueueMode = mode
        },
        syncPlayerQueue = { syncedQueue = it },
        remainingMediaItems = { remainingItems },
        playFromQueue = { queue, index ->
            playedFromQueue = queue
            playedFromIndex = index
        },
        rawPlayCounts = { ids -> ids.associateWith { if (it == 1) 10 else 0 } },
        log = { logs += it },
        planner = RandomQueuePlanner(
            batchSize = 2,
            shuffle = { it },
            isPlayable = { it.sampleRate > 0 },
        ),
    )

    @Test
    fun playRandomQueueStartsSequentialQueueAndLeavesInfiniteMode() {
        state = state.copy(
            songs = songs(1, 2, 3),
            globalUniformRandomEnabled = false,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(9),
        )

        runBlocking { facade.playRandomQueue() }

        assertEquals(listOf(1, 2), setQueueSongs?.map { it.id })
        assertEquals(PlayMode.SEQUENTIAL, setQueueMode)
        assertFalse(state.isInfinitePlay)
        assertEquals(emptySet<Int>(), state.infinitePlayedSongIds)
    }

    @Test
    fun playRandomQueueUsesUniformRandomWhenEnabled() {
        state = state.copy(
            songs = songs(1, 2, 3),
            globalUniformRandomEnabled = true,
        )

        runBlocking { facade.playRandomQueue() }

        assertEquals(listOf(2, 3), setQueueSongs?.map { it.id })
    }

    @Test
    fun playRandomQueueReturnsFalseWhenNoSongs() {
        state = state.copy(songs = emptyList())

        val started = runBlocking { facade.playRandomQueue() }

        assertFalse(started)
        assertEquals(null, setQueueSongs)
        assertFalse(state.isInfinitePlay)
    }

    @Test
    fun playRandomQueueReturnsFalseWhenNoPlayableSongs() {
        state = state.copy(
            songs = listOf(Song(id = 1, title = "Unplayable", artist = "Artist", sampleRate = 0)),
        )

        val started = runBlocking { facade.playRandomQueue() }

        assertFalse(started)
        assertEquals(null, setQueueSongs)
    }

    @Test
    fun startInfinitePlayKeepsCurrentQueueAndStoresCoveredIds() {
        state = state.copy(
            songs = songs(1, 2, 3),
            playQueue = PlayQueue().setQueue(songs(2, 3), startIndex = 1),
        )

        runBlocking { facade.startInfinitePlay() }

        assertEquals(listOf(2, 3), state.playQueue.songs.map { it.id })
        assertEquals(null, setQueueSongs)
        assertTrue(state.isInfinitePlay)
        assertEquals(setOf(2, 3), state.infinitePlayedSongIds)
    }

    @Test
    fun startInfinitePlayCanStartWithEmptyQueueAndWaitForRefill() {
        state = state.copy(songs = songs(1, 2, 3))

        runBlocking { facade.startInfinitePlay() }

        assertEquals(emptyList<Int>(), state.playQueue.songs.map { it.id })
        assertEquals(null, setQueueSongs)
        assertTrue(state.isInfinitePlay)
        assertEquals(emptySet<Int>(), state.infinitePlayedSongIds)
    }

    @Test
    fun startInfinitePlayRefillsWhenAtQueueTail() {
        // 队列末尾开启无限播放：当前歌曲 A(4) 已是最后一首，应立即补队列，
        // 让下一首（自然结束或系统媒体键下一首）是新歌而不是回绕到旧窗口第一首。
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4), startIndex = 3),
        )
        remainingItems = 0

        runBlocking { facade.startInfinitePlay() }

        assertTrue(state.isInfinitePlay)
        // 队列追加了 5、6，当前歌曲仍是 A(4)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), state.playQueue.songs.map { it.id })
        assertEquals(3, state.playQueue.currentIndex)
        assertEquals(state.playQueue, syncedQueue)
    }

    @Test
    fun startInfinitePlayDoesNotRefillWhenNotNearTail() {
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4), startIndex = 0),
        )
        remainingItems = 10

        runBlocking { facade.startInfinitePlay() }

        assertTrue(state.isInfinitePlay)
        assertEquals(listOf(1, 2, 3, 4), state.playQueue.songs.map { it.id })
        assertEquals(null, syncedQueue)
    }

    @Test
    fun startInfinitePlayReturnsFalseWhenNoSongs() {
        state = state.copy(songs = emptyList())

        val started = runBlocking { facade.startInfinitePlay() }

        assertFalse(started)
        assertFalse(state.isInfinitePlay)
    }

    @Test
    fun stopInfinitePlayClearsInfiniteState() {
        state = state.copy(isInfinitePlay = true, infinitePlayedSongIds = setOf(1, 2))

        facade.stopInfinitePlay()

        assertFalse(state.isInfinitePlay)
        assertEquals(emptySet<Int>(), state.infinitePlayedSongIds)
    }

    @Test
    fun refillInfinitePlayQueueAppendsSongsAndSyncsControllerQueue() {
        state = state.copy(
            songs = songs(1, 2, 3, 4),
            playQueue = PlayQueue().setQueue(songs(1, 2), startIndex = 0),
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2),
        )

        runBlocking { facade.refillInfinitePlayQueue() }

        assertEquals(listOf(1, 2, 3, 4), state.playQueue.songs.map { it.id })
        assertEquals(setOf(1, 2, 3, 4), state.infinitePlayedSongIds)
        assertEquals(state.playQueue, syncedQueue)
    }

    @Test
    fun refillInfinitePlayQueueUsesStartedSongAsCurrentIndexBeforeSyncing() {
        state = state.copy(
            songs = songs(1, 2, 3, 4),
            playQueue = PlayQueue().setQueue(songs(1, 2), startIndex = 0),
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2),
        )

        runBlocking { facade.refillInfinitePlayQueue(startedSongId = 2) }

        assertEquals(listOf(1, 2, 3, 4), state.playQueue.songs.map { it.id })
        assertEquals(1, state.playQueue.currentIndex)
        assertEquals(1, syncedQueue?.currentIndex)
    }

    @Test
    fun refillInfinitePlayQueueDoesNothingOutsideInfiniteMode() {
        state = state.copy(
            songs = songs(1, 2, 3),
            playQueue = PlayQueue().setQueue(songs(1), startIndex = 0),
            isInfinitePlay = false,
        )

        runBlocking { facade.refillInfinitePlayQueue() }

        assertEquals(listOf(1), state.playQueue.songs.map { it.id })
        assertEquals(null, syncedQueue)
    }

    @Test
    fun refillInfinitePlayQueueAdvanceAfterWrapJumpsToFirstNewSong() {
        // 窗口尾部回绕后补队列：当前歌曲跳到第一首新补充的歌曲（而不是停留在回绕到的旧歌）
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4), startIndex = 3),
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2, 3, 4),
        )

        runBlocking { facade.refillInfinitePlayQueue(startedSongId = 4, advanceAfterWrap = true) }

        // 追加 5、6，当前歌曲跳到索引 4（歌曲 5）
        assertEquals(listOf(1, 2, 3, 4, 5, 6), state.playQueue.songs.map { it.id })
        assertEquals(4, state.playQueue.currentIndex)
        assertEquals(4, playedFromIndex)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), playedFromQueue?.songs?.map { it.id })
        // 跳转走 playFromQueue，不走 syncPlayerQueue
        assertEquals(null, syncedQueue)
    }

    @Test
    fun refillInfinitePlayQueueAdvanceAfterWrapKeepsCurrentSongWhenNoNewSongs() {
        // 库已全部覆盖、无新歌可补时，advance 不能跳到不存在的歌曲
        state = state.copy(
            songs = songs(1, 2, 3, 4),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4), startIndex = 3),
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2, 3, 4),
        )

        runBlocking { facade.refillInfinitePlayQueue(startedSongId = 4, advanceAfterWrap = true) }

        assertEquals(listOf(1, 2, 3, 4), state.playQueue.songs.map { it.id })
        assertEquals(3, state.playQueue.currentIndex)
        assertEquals(null, playedFromIndex)
        assertEquals(null, syncedQueue)
    }

    // ── 情境化随心播放（一期） ──

    private fun moodFacade(
        configs: List<cn.com.dcsgo.mihx.core.model.TimeSlotConfig>,
        enabled: Boolean,
        tagsBySong: Map<Int, List<String>>,
        nowMinuteOfDay: Int = 22 * 60, // 默认 22:00，恰好命中深夜时段
        ratiosBySong: Map<Int, Map<String, Float>>? = null,
    ): PlayerRandomQueueFacade = PlayerRandomQueueFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        setPlayQueue = { songs, startIndex, mode ->
            state = state.copy(playQueue = PlayQueue().setQueue(songs, startIndex, mode))
            setQueueSongs = songs
            setQueueMode = mode
        },
        syncPlayerQueue = { syncedQueue = it },
        remainingMediaItems = { remainingItems },
        playFromQueue = { queue, index ->
            playedFromQueue = queue
            playedFromIndex = index
        },
        rawPlayCounts = { emptyMap() },
        log = { logs += it },
        planner = RandomQueuePlanner(
            batchSize = 2,
            shuffle = { it },
            isPlayable = { it.sampleRate > 0 },
        ),
        moodSlotConfigs = { configs },
        moodSlotEnabled = { enabled },
        moodEmotionTagsOf = { song -> tagsBySong[song.id].orEmpty().toSet() },
        // null = 不注入占比 -> 走默认实现(每词按 1.0), 行为与改造前的纯 OR 判定一致
        moodEmotionTagRatiosOf = { song ->
            ratiosBySong?.get(song.id) ?: tagsBySong[song.id].orEmpty().associateWith { 1f }
        },
        nowMinuteOfDay = { nowMinuteOfDay },
    )

    @Test
    fun moodFilterLimitsRandomPoolToTaggedSongs() {
        // 22:00 命中 22:00–06:00 时段，词条【静谧】只覆盖歌曲 1、3
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 22 * 60, endMinutes = 6 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(
            configs = configs,
            enabled = true,
            tagsBySong = mapOf(1 to listOf("静谧"), 3 to listOf("静谧")),
        )
        state = state.copy(songs = songs(1, 2, 3, 4))

        runBlocking { mood.playRandomQueue() }

        // 批量 2 首全部来自过滤后的池 {1,3}
        assertTrue(setQueueSongs!!.all { it.id == 1 || it.id == 3 })
    }

    @Test
    fun moodFilterFallsBackToFullLibraryWhenNoTaggedSongs() {
        // 词条组合 0 首：回退全库随机（降级 §4.3），不因空池而启动失败
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 22 * 60, endMinutes = 6 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(configs = configs, enabled = true, tagsBySong = emptyMap())
        state = state.copy(songs = songs(1, 2, 3))

        val started = runBlocking { mood.playRandomQueue() }

        assertTrue(started)
        assertEquals(listOf(1, 2), setQueueSongs?.map { it.id })
        assertTrue(logs.any { it.contains("fall back to full library") })
    }

    @Test
    fun moodFilterInactiveOutsideSlotTime() {
        // 12:00 不命中深夜时段 → 随机与现状一致（全库）
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 22 * 60, endMinutes = 6 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(
            configs = configs,
            enabled = true,
            tagsBySong = mapOf(1 to listOf("静谧")),
            nowMinuteOfDay = 12 * 60,
        )
        state = state.copy(songs = songs(1, 2, 3))

        runBlocking { mood.playRandomQueue() }

        assertEquals(listOf(1, 2), setQueueSongs?.map { it.id })
    }

    @Test
    fun moodFilterDisabledKeepsOriginalBehavior() {
        // 开关关闭：即使配置全天覆盖、词条匹配，也不过滤
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "全天静谧",
                startMinutes = 0, endMinutes = 24 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(configs = configs, enabled = false, tagsBySong = mapOf(1 to listOf("静谧")))
        state = state.copy(songs = songs(1, 2, 3))

        runBlocking { mood.playRandomQueue() }

        assertEquals(listOf(1, 2), setQueueSongs?.map { it.id })
    }

    @Test
    fun moodFilterCompatibleWithUniformRandom() {
        // 均匀随机 + 情绪过滤叠加：候选池过滤后仍走分层抢占（低播放次数优先）
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 0, endMinutes = 24 * 60,
                tags = listOf("静谧"),
            ),
        )
        // 歌 3、4 是「静谧」；歌 3 播放 10 次、歌 4 播放 0 次 → 均匀随机应优先歌 4
        val mood = PlayerRandomQueueFacade(
            state = { state },
            updateState = { transform -> state = transform(state) },
            setPlayQueue = { songs, startIndex, mode ->
                state = state.copy(playQueue = PlayQueue().setQueue(songs, startIndex, mode))
                setQueueSongs = songs
                setQueueMode = mode
            },
            syncPlayerQueue = { syncedQueue = it },
            remainingMediaItems = { remainingItems },
            playFromQueue = { _, _ -> },
            rawPlayCounts = { ids -> ids.associateWith { if (it == 3) 10 else 0 } },
            log = { logs += it },
            planner = RandomQueuePlanner(
                batchSize = 2,
                shuffle = { it },
                isPlayable = { it.sampleRate > 0 },
            ),
            moodSlotConfigs = { configs },
            moodSlotEnabled = { true },
            moodEmotionTagsOf = { song -> if (song.id == 3 || song.id == 4) setOf("静谧") else emptySet() },
            nowMinuteOfDay = { 22 * 60 },
        )
        state = state.copy(songs = songs(1, 2, 3, 4), globalUniformRandomEnabled = true)

        runBlocking { mood.playRandomQueue() }

        // 候选池只有 {3,4}，批量 2 全取；顺序上 0 次播放的歌 4 优先于 10 次的歌 3
        assertEquals(listOf(4, 3), setQueueSongs?.map { it.id })
    }

    @Test
    fun moodFilterRequiresTheWordToDominateEnoughOfTheSong() {
        // 词条占比低于门槛的不算命中 —— 16% 的「静谧」和 70% 的「静谧」不是一回事
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 22 * 60, endMinutes = 6 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(
            configs = configs,
            enabled = true,
            tagsBySong = mapOf(1 to listOf("静谧"), 3 to listOf("静谧")),
            ratiosBySong = mapOf(
                1 to mapOf("静谧" to 0.70f),  // 够高 -> 命中
                3 to mapOf("静谧" to 0.16f),  // 太低 -> 不命中
            ),
        )
        state = state.copy(songs = songs(1, 2, 3, 4))
        runBlocking { mood.playRandomQueue() }
        // 候选池只剩 {1}（2、4 没词条，3 占比不够）
        assertEquals(listOf(1), setQueueSongs?.map { it.id })
    }

    @Test
    fun moodFilterFallsBackToPlainTagMatchWhenNoRatiosAreInjected() {
        // 不注入占比时行为与改造前完全一致（向后兼容）：有词就算命中
        val configs = listOf(
            cn.com.dcsgo.mihx.core.model.TimeSlotConfig(
                id = 1, name = "深夜静谧",
                startMinutes = 22 * 60, endMinutes = 6 * 60,
                tags = listOf("静谧"),
            ),
        )
        val mood = moodFacade(
            configs = configs,
            enabled = true,
            tagsBySong = mapOf(1 to listOf("静谧"), 3 to listOf("静谧")),
        )
        state = state.copy(songs = songs(1, 2, 3, 4))
        runBlocking { mood.playRandomQueue() }
        assertEquals(setOf(1, 3), setQueueSongs?.map { it.id }?.toSet())
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }

    // ── 探索播放（2026-10-20） ──

    private fun exploreFacade(
        tagsBySong: Map<Int, List<String>> = emptyMap(),
        prefs: Map<String, cn.com.dcsgo.mihx.core.model.WordPreference> = emptyMap(),
    ): PlayerRandomQueueFacade = PlayerRandomQueueFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        setPlayQueue = { songs, startIndex, mode ->
            state = state.copy(playQueue = PlayQueue().setQueue(songs, startIndex, mode))
            setQueueSongs = songs
            setQueueMode = mode
        },
        syncPlayerQueue = { syncedQueue = it },
        remainingMediaItems = { remainingItems },
        playFromQueue = { _, _ -> },
        rawPlayCounts = { emptyMap() },
        log = { logs += it },
        planner = RandomQueuePlanner(
            batchSize = 20,
            shuffle = { it },
            isPlayable = { it.sampleRate > 0 },
        ),
        exploreModeEnabled = { true },
        moodEmotionTagsOf = { song -> tagsBySong[song.id].orEmpty().toSet() },
        wordPreferenceOf = { word -> prefs[word] ?: cn.com.dcsgo.mihx.core.model.WordPreference(word, 0, 0) },
    )

    @Test
    fun explorePlayRandomQueueGenerates10SongsAndSetsExplorePlay() {
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
            globalUniformRandomEnabled = false,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(9),
        )

        runBlocking { exploreFacade().playRandomQueue() }

        assertEquals(10, setQueueSongs?.size)
        assertTrue(state.isExplorePlay)
        assertFalse(state.isInfinitePlay)
        assertEquals(emptySet<Int>(), state.infinitePlayedSongIds)
        assertTrue(logs.any { it.contains("explore=true") })
    }

    @Test
    fun exploreSessionBlocksInfinitePlay() {
        state = state.copy(
            songs = songs(1, 2, 3),
            isExplorePlay = true,
        )

        val started = runBlocking { exploreFacade().startInfinitePlay() }

        assertFalse(started)
        assertFalse(state.isInfinitePlay)
        assertTrue(logs.any { it.contains("blocked by explore session") })
    }

    @Test
    fun exploreSessionEndedAllowsInfinitePlay() {
        // 用户手动点歌退出探索后（isExplorePlay=false，设置仍是探索模式）可开启无限随机
        state = state.copy(
            songs = songs(1, 2, 3),
            isExplorePlay = false,
            isExploreModeActive = true,
        )

        val started = runBlocking { exploreFacade().startInfinitePlay() }

        assertTrue(started)
        assertTrue(state.isInfinitePlay)
        assertTrue(logs.none { it.contains("blocked") })
    }

    @Test
    fun exploreRefillPrefersLikedAndNoDislikedWords() {
        // 喜欢词「静谧」、不喜欢词「伤感」；
        // 歌1 静谧、歌2 伤感、歌3 静谧+伤感、歌4/5/7/8/9 静谧、歌6 无词
        // → 补充候选应为 {1, 4, 5, 7, 8, 9}（含喜欢词且不含不喜欢词）
        // 池必须够 5 首才用（不够就降级，见下一个测试）
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6, 7, 8, 9),
            playQueue = PlayQueue().setQueue(songs(20, 21), startIndex = 0), // 队列中已有 20、21
            isExplorePlay = true,
        )
        val explore = exploreFacade(
            tagsBySong = mapOf(
                1 to listOf("静谧"),
                2 to listOf("伤感"),
                3 to listOf("静谧", "伤感"),
                4 to listOf("静谧"),
                5 to listOf("静谧"),
                6 to emptyList(),
                7 to listOf("静谧"),
                8 to listOf("静谧"),
                9 to listOf("静谧"),
            ),
            prefs = mapOf(
                "静谧" to cn.com.dcsgo.mihx.core.model.WordPreference("静谧", 3, 1),   // LIKE
                "伤感" to cn.com.dcsgo.mihx.core.model.WordPreference("伤感", 0, 2),   // DISLIKE
            ),
        )

        runBlocking { explore.refillExplorePlayQueue() }

        // 补满 5 首，全部来自「含喜欢词且不含不喜欢词」的池
        val added = state.playQueue.songs.map { it.id }.filterNot { it in setOf(20, 21) }
        assertEquals(5, added.size)
        assertTrue(added.all { it in setOf(1, 4, 5, 7, 8, 9) })
        assertTrue(logs.any { it.contains("explore refill") })
    }

    @Test
    fun exploreRefillFallsBackWhenPreferredPoolIsSmallerThanTheRefillCount() {
        // 回归（2026-10-25）：真实场景 —— 用户一路点下一首，每首都投「不喜欢」票，
        // 很快所有词条都成了 DISLIKE。此时「仅不含不喜欢词」的池只剩**没有情绪分析**
        // 的那几首（超 20 分钟、分析失败的歌没有词条，自然不含任何不喜欢词），
        // 池子非空但只有 2 首 < REFILL_COUNT(5)。
        //
        // 旧判据是「池子非空即用」→ 只补 2 首 → 再切歌时这 2 首已在队列里 →
        // 「no more songs outside queue」→ 永远不再补充，队列播完回到第一首。
        // 新判据是「池子够不够补满 5 首」→ 降级到全库 → 一定能补满。
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), startIndex = 0),
            isExplorePlay = true,
        )
        val explore = exploreFacade(
            // 歌 11..18 都含被讨厌的词；歌 19、20 没有词条 -> 「不含讨厌词」的池只剩这 2 首
            tagsBySong = (11..18).associateWith { listOf("元气") } + (19..20).associateWith { listOf() },
            prefs = mapOf(
                "元气" to cn.com.dcsgo.mihx.core.model.WordPreference("元气", 0, 20),  // DISLIKE
            ),
        )

        runBlocking { explore.refillExplorePlayQueue() }

        // 必须补满 5 首（而不是只补「不含讨厌词」池里那 2 首）
        val added = state.playQueue.songs.map { it.id }.filterNot { it in (1..10).toSet() }
        assertEquals(5, added.size)
        assertTrue(logs.any { it.contains("fall back to full library") })
    }

    @Test
    fun exploreRefillFallsBackToNoDislikePoolWhenNoLikedSongs() {
        // 无「喜欢词」歌曲 → 回退到「仅不含不喜欢词」的池
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6, 7),
            playQueue = PlayQueue().setQueue(songs(8, 9), startIndex = 0),
            isExplorePlay = true,
        )
        val explore = exploreFacade(
            tagsBySong = mapOf(
                1 to listOf("伤感"),
                2 to listOf("伤感"),
                3 to listOf("快乐"),
                4 to listOf("快乐"),
                5 to listOf("快乐"),
                6 to listOf("快乐"),
                7 to listOf("快乐"),
            ),
            prefs = mapOf(
                "快乐" to cn.com.dcsgo.mihx.core.model.WordPreference("快乐", 1, 1), // NEUTRAL（平票）
                "伤感" to cn.com.dcsgo.mihx.core.model.WordPreference("伤感", 0, 5), // DISLIKE
            ),
        )

        runBlocking { explore.refillExplorePlayQueue() }

        // 池 = 仅排除不喜欢词（伤感）→ {3,4,5,6,7} 中补 5 首
        val added = state.playQueue.songs.map { it.id }.filterNot { it in setOf(8, 9) }
        assertEquals(5, added.size)
        assertTrue(added.all { it in setOf(3, 4, 5, 6, 7) })
        assertTrue(logs.any { it.contains("no liked match") })
    }

    @Test
    fun exploreRefillFallsBackToFullLibraryWhenAllDisliked() {
        // 全部歌曲都含不喜欢词 → 回退全库
        state = state.copy(
            songs = songs(1, 2, 3),
            playQueue = PlayQueue().setQueue(songs(9, 10), startIndex = 0),
            isExplorePlay = true,
        )
        val explore = exploreFacade(
            tagsBySong = mapOf(1 to listOf("伤感"), 2 to listOf("伤感"), 3 to listOf("伤感")),
            prefs = mapOf("伤感" to cn.com.dcsgo.mihx.core.model.WordPreference("伤感", 0, 3)),
        )

        runBlocking { explore.refillExplorePlayQueue() }

        assertEquals(3, state.playQueue.songs.size - 2)
        assertTrue(logs.any { it.contains("fall back to full library") })
    }

    @Test
    fun exploreRefillDoesNothingWhenNotInExploreSession() {
        state = state.copy(
            songs = songs(1, 2, 3),
            playQueue = PlayQueue().setQueue(songs(1), startIndex = 0),
            isExplorePlay = false,
        )

        runBlocking { exploreFacade().refillExplorePlayQueue() }

        assertEquals(listOf(1), state.playQueue.songs.map { it.id })
    }

    @Test
    fun exploreRefillKeepsStartedSongCurrentAfterStaleIndexSync() {
        // 回归（2026-10-20）：切歌后业务队列 currentIndex 仍是「刚结束的歌」，补队列必须用
        // startedSongId 校正，否则 sync 会回拉到旧歌重新播放。
        // 队列 [1..10] currentIndex=7（刚结束的歌 8）→ 新当前歌 9（index 8），补 5 首后应保持歌 9。
        state = state.copy(
            songs = songs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            playQueue = PlayQueue().setQueue(songs(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), startIndex = 7),
            isExplorePlay = true,
        )
        val explore = exploreFacade(
            tagsBySong = (1..15).associateWith { listOf("静谧") },
            prefs = mapOf("静谧" to cn.com.dcsgo.mihx.core.model.WordPreference("静谧", 5, 1)),
        )

        runBlocking { explore.refillExplorePlayQueue(startedSongId = 9) }

        // 新当前歌（9）的索引被校正为 8，且队尾追加 5 首 → 不会回拉到歌 8 重播
        assertEquals(8, state.playQueue.currentIndex)
        assertEquals(9, state.playQueue.currentSong?.id)
        assertEquals(15, state.playQueue.songs.size)
        assertEquals(8, syncedQueue?.currentIndex)
        assertTrue(logs.any { it.contains("added=5") })
    }
}
