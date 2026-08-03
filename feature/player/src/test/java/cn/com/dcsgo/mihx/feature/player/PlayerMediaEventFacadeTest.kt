package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMediaEventFacadeTest {

    private var state = PlayerUiState()
    private var stoppedTracking = false
    private var clearedTrackedSong = false
    private var remainingItems = Int.MAX_VALUE
    private var refilledStartedSongId: Int? = null
    private var syncedQueue: PlayQueue? = null
    private val logs = mutableListOf<String>()
    private val facade = PlayerMediaEventFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        stopPlaybackTracking = { stoppedTracking = true },
        clearTrackedSong = { clearedTrackedSong = true },
        remainingMediaItems = { remainingItems },
        refillInfinitePlayQueue = { startedSongId -> refilledStartedSongId = startedSongId },
        syncPlayerQueue = { syncedQueue = it },
        log = { logs += it },
    )

    @Test
    fun handleMediaItemEndedStopsTrackingAndClearsTrackedSong() {
        facade.handleMediaItemEnded()

        assertTrue(stoppedTracking)
        assertTrue(clearedTrackedSong)
    }

    @Test
    fun handleMediaItemEndedRefillsInfiniteQueueNearTail() {
        state = state.copy(isInfinitePlay = true)
        remainingItems = 5

        facade.handleMediaItemEnded(startedSongId = 2)

        assertEquals(2, refilledStartedSongId)
        assertEquals(listOf("infinite play refill: remaining=5"), logs)
    }

    @Test
    fun handleMediaItemEndedRestoresPlayModeAfterAddNextSong() {
        val queue = PlayQueue()
            .setQueue(songs(1, 2), startIndex = 1, mode = PlayMode.SEQUENTIAL)
        state = state.copy(
            playQueue = queue,
            currentSong = song(2),
            nextPlaySongId = 2,
            playModeBeforeNext = PlayMode.SHUFFLE,
        )

        facade.handleMediaItemEnded(startedSongId = 1)

        assertEquals(PlayMode.SHUFFLE, state.playQueue.playMode)
        assertEquals(null, state.nextPlaySongId)
        assertEquals(null, state.playModeBeforeNext)
        assertEquals(0, state.playQueue.currentIndex)
        assertEquals(state.playQueue, syncedQueue)
    }

    @Test
    fun handlePlaybackEndedStopsUiPlaybackAndResetsPosition() {
        state = state.copy(isPlaying = true, currentPositionMs = 42L)

        facade.handlePlaybackEnded()

        assertFalse(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
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
