package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.RandomQueuePlanner
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

        facade.playRandomQueue()

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

        facade.playRandomQueue()

        assertEquals(listOf(2, 3), setQueueSongs?.map { it.id })
    }

    @Test
    fun playRandomQueueReturnsFalseWhenNoSongs() {
        state = state.copy(songs = emptyList())

        val started = facade.playRandomQueue()

        assertFalse(started)
        assertEquals(null, setQueueSongs)
        assertFalse(state.isInfinitePlay)
    }

    @Test
    fun playRandomQueueReturnsFalseWhenNoPlayableSongs() {
        state = state.copy(
            songs = listOf(Song(id = 1, title = "Unplayable", artist = "Artist", sampleRate = 0)),
        )

        val started = facade.playRandomQueue()

        assertFalse(started)
        assertEquals(null, setQueueSongs)
    }

    @Test
    fun startInfinitePlayKeepsCurrentQueueAndStoresCoveredIds() {
        state = state.copy(
            songs = songs(1, 2, 3),
            playQueue = PlayQueue().setQueue(songs(2, 3), startIndex = 1),
        )

        facade.startInfinitePlay()

        assertEquals(listOf(2, 3), state.playQueue.songs.map { it.id })
        assertEquals(null, setQueueSongs)
        assertTrue(state.isInfinitePlay)
        assertEquals(setOf(2, 3), state.infinitePlayedSongIds)
    }

    @Test
    fun startInfinitePlayCanStartWithEmptyQueueAndWaitForRefill() {
        state = state.copy(songs = songs(1, 2, 3))

        facade.startInfinitePlay()

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

        facade.startInfinitePlay()

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

        facade.startInfinitePlay()

        assertTrue(state.isInfinitePlay)
        assertEquals(listOf(1, 2, 3, 4), state.playQueue.songs.map { it.id })
        assertEquals(null, syncedQueue)
    }

    @Test
    fun startInfinitePlayReturnsFalseWhenNoSongs() {
        state = state.copy(songs = emptyList())

        val started = facade.startInfinitePlay()

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

        facade.refillInfinitePlayQueue()

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

        facade.refillInfinitePlayQueue(startedSongId = 2)

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

        facade.refillInfinitePlayQueue()

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

        facade.refillInfinitePlayQueue(startedSongId = 4, advanceAfterWrap = true)

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

        facade.refillInfinitePlayQueue(startedSongId = 4, advanceAfterWrap = true)

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
        moodEmotionTagsOf = { song -> tagsBySong[song.id].orEmpty() },
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

        mood.playRandomQueue()

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

        val started = mood.playRandomQueue()

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

        mood.playRandomQueue()

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

        mood.playRandomQueue()

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
            moodEmotionTagsOf = { song -> if (song.id == 3 || song.id == 4) listOf("静谧") else emptyList() },
            nowMinuteOfDay = { 22 * 60 },
        )
        state = state.copy(songs = songs(1, 2, 3, 4), globalUniformRandomEnabled = true)

        mood.playRandomQueue()

        // 候选池只有 {3,4}，批量 2 全取；顺序上 0 次播放的歌 4 优先于 10 次的歌 3
        assertEquals(listOf(4, 3), setQueueSongs?.map { it.id })
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
