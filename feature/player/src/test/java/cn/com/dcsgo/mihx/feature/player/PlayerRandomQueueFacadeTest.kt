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

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
