package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQueueFacadeTest {

    private var state = PlayerUiState()
    private var playedQueue: PlayQueue? = null
    private var playedIndex: Int? = null
    private var syncedQueue: PlayQueue? = null
    private var clearedController = false
    private var clearedPlaybackState = false
    private var savedPlaybackState = false
    private val logs = mutableListOf<String>()
    private val facade = PlayerQueueFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        playFromQueue = { queue, index ->
            playedQueue = queue
            playedIndex = index
        },
        syncPlayerQueue = { queue -> syncedQueue = queue },
        clearControllerPlaylist = {
            clearedController = true
            true
        },
        clearPlaybackState = { clearedPlaybackState = true },
        savePlaybackState = { savedPlaybackState = true },
        log = { logs += it },
    )

    @Test
    fun setPlayQueueUpdatesStateAndStartsPlayback() {
        val songs = songs(1, 2)

        facade.setPlayQueue(songs, startIndex = 1)

        assertEquals(listOf(1, 2), state.playQueue.songs.map { it.id })
        assertEquals(1, state.playQueue.currentIndex)
        assertEquals(1, playedIndex)
        assertEquals(listOf(1, 2), playedQueue?.songs?.map { it.id })
        assertTrue(savedPlaybackState)
    }

    @Test
    fun clearPlayQueueClearsUiControllerAndPersistedState() {
        state = PlayerUiState(
            playQueue = PlayQueue().setQueue(songs(1), startIndex = 0),
            currentSong = song(1),
            isPlaying = true,
            currentPositionMs = 42L,
            durationMs = 100L,
        )

        facade.clearPlayQueue()

        assertTrue(state.playQueue.isEmpty)
        assertEquals(null, state.currentSong)
        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertTrue(clearedController)
        assertTrue(clearedPlaybackState)
    }

    @Test
    fun addSongsToPlayQueueAllowsDuplicatesAndSyncsController() {
        state = PlayerUiState(playQueue = PlayQueue().setQueue(songs(1, 2), startIndex = 0))

        val added = facade.addToPlayQueue(songs(2, 3))

        assertEquals(2, added)
        assertEquals(listOf(1, 2, 2, 3), state.playQueue.songs.map { it.id })
        assertEquals(listOf(1, 2, 2, 3), syncedQueue?.songs?.map { it.id })
        assertTrue(savedPlaybackState)
    }

    @Test
    fun removeFromPlayQueueAtRemovesOnlyRequestedDuplicate() {
        state = PlayerUiState(playQueue = PlayQueue().setQueue(songs(1, 2, 2, 3), startIndex = 2))

        facade.removeFromPlayQueueAt(index = 1)

        assertEquals(listOf(1, 2, 3), state.playQueue.songs.map { it.id })
        assertEquals(1, state.playQueue.currentIndex)
        assertEquals(listOf(1, 2, 3), syncedQueue?.songs?.map { it.id })
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
