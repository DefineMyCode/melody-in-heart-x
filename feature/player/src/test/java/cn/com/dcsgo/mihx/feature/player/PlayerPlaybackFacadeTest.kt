package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackCommandPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackFacadeTest {

    private var state = PlayerUiState()
    private var hasCurrentMediaItem = false
    private var setQueueStartIndex: Int? = null
    private var playedQueueIndex: Int? = null
    private var playedFromQueueIndex: Int? = null
    private var playedSingleSongId: Int? = null
    private var paused = false
    private var resumed = false
    private var controllerPrevious = false
    private var controllerNext = false
    private var remainingItems = Int.MAX_VALUE
    private var refilled = false
    private val logs = mutableListOf<String>()
    private val facade = PlayerPlaybackFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        hasCurrentMediaItem = { hasCurrentMediaItem },
        setPlayQueue = { songs, startIndex ->
            state = state.copy(playQueue = PlayQueue().setQueue(songs, startIndex))
            setQueueStartIndex = startIndex
        },
        playQueueItem = { index ->
            playedQueueIndex = index
            true
        },
        playFromQueue = { _, index -> playedFromQueueIndex = index },
        playSingle = { song ->
            playedSingleSongId = song.id
            true
        },
        pausePlayback = { paused = true },
        resumePlayback = { resumed = true },
        playPreviousInController = { controllerPrevious = true },
        playNextInController = { controllerNext = true },
        remainingMediaItems = { remainingItems },
        refillInfinitePlayQueue = { refilled = true },
        log = { logs += it },
        planner = PlaybackCommandPlanner { it.sampleRate > 0 },
    )

    @Test
    fun playSongUsesExistingQueueItemWhenPresent() {
        state = state.copy(playQueue = PlayQueue().setQueue(songs(1, 2), startIndex = 0))

        facade.playSong(song(2))

        assertEquals(1, playedQueueIndex)
        assertEquals(null, playedSingleSongId)
    }

    @Test
    fun playSongStartsSinglePlaybackWhenSongIsPlayableAndMissingFromQueue() {
        facade.playSong(song(3, playable = true))

        assertEquals(3, playedSingleSongId)
    }

    @Test
    fun playSongFromContextReplacesQueueAtSongIndex() {
        facade.playSongFromContext(song(2, playable = true), songs(1, 2, 3))

        assertEquals(1, setQueueStartIndex)
        assertEquals(listOf(1, 2, 3), state.playQueue.songs.map { it.id })
    }

    @Test
    fun togglePlayPausePausesWhenPlaying() {
        state = state.copy(isPlaying = true)

        facade.togglePlayPause()

        assertTrue(paused)
    }

    @Test
    fun togglePlayPauseResumesWhenControllerHasCurrentMediaItem() {
        hasCurrentMediaItem = true

        facade.togglePlayPause()

        assertTrue(resumed)
    }

    @Test
    fun playNextConsumesSkipRefillBeforeAdvancingController() {
        state = state.copy(skipNextRefill = true)

        facade.playNext()

        assertFalse(state.skipNextRefill)
        assertTrue(controllerNext)
    }

    @Test
    fun playNextRefillsInfiniteQueueNearTail() {
        state = state.copy(isInfinitePlay = true)
        remainingItems = 5

        facade.playNext()

        assertTrue(refilled)
        assertTrue(controllerNext)
    }

    @Test
    fun playPreviousIgnoresEmptyQueue() {
        facade.playPrevious()

        assertFalse(controllerPrevious)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it, playable = true) }

    private fun song(id: Int, playable: Boolean = false): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = if (playable) 44_100 else 0,
        )
    }
}
