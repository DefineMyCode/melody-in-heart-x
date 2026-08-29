package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationTracker
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionController
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionCoordinator
import cn.com.dcsgo.mihx.domain.playback.SeekCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSessionFacadeTest {

    private var state = PlayerUiState()
    private val controller = FakeSessionController()
    private val tracker = FakeDurationTracker()
    private var trackedSongId: Int? = null
    private var sameNameSongId: Int? = null
    private var saved = false
    private var playbackStateChanges = 0
    private val scheduledTasks = mutableListOf<() -> Unit>()
    private val logs = mutableListOf<String>()
    private val facade = PlayerSessionFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        sessionCoordinator = PlaybackSessionCoordinator(
            controller = controller,
            durationTracker = tracker,
            planControllerQueue = ::planControllerQueue,
        ),
        seekCoordinator = SeekCoordinator(
            session = PlaybackSessionCoordinator(
                controller = controller,
                durationTracker = tracker,
                planControllerQueue = ::planControllerQueue,
            ),
            controllerIsPlaying = { controller.isPlaying },
            currentIsPlaying = { state.isPlaying },
        ),
        setTrackedSongId = { trackedSongId = it },
        updateSameNameSongs = { sameNameSongId = it.id },
        savePlaybackState = { saved = true },
        onPlaybackStateChanged = { playbackStateChanges++ },
        scheduleAfterSeek = { task -> scheduledTasks += task },
        log = { logs += it },
    )

    @Test
    fun pausePlaybackUpdatesStateAndRequestsSaveWhenPlaying() {
        state = state.copy(isPlaying = true)

        facade.pausePlayback()

        assertTrue(controller.paused)
        assertFalse(state.isPlaying)
        assertTrue(tracker.paused)
        assertTrue(saved)
        assertEquals(1, playbackStateChanges)
    }

    @Test
    fun resumePlaybackUpdatesStateWhenSongIsLoaded() {
        state = state.copy(currentSong = song(1), isPlaying = false)

        facade.resumePlayback()

        assertTrue(controller.played)
        assertTrue(tracker.resumed)
        assertTrue(state.isPlaying)
        assertEquals(1, playbackStateChanges)
    }

    @Test
    fun startQueuePlaybackAppliesCurrentSongAndTracking() {
        controller.duration = 500L
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 1)

        val started = facade.startQueuePlayback(queue, index = 1)

        assertTrue(started)
        assertEquals(2, state.currentSong?.id)
        assertTrue(state.isPlaying)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(500L, state.durationMs)
        assertEquals(2, trackedSongId)
        assertEquals(2, sameNameSongId)
    }

    @Test
    fun startSinglePlaybackReturnsFalseWhenControllerRejectsSong() {
        controller.canPlaySingle = false

        val started = facade.startSinglePlayback(song(1))

        assertFalse(started)
        assertEquals(null, state.currentSong)
    }

    @Test
    fun seekSchedulesControllerStateSync() {
        state = state.copy(isPlaying = true)
        controller.isPlayingValue = false

        facade.seekTo(42L)
        scheduledTasks.single().invoke()

        assertEquals(42L, state.currentPositionMs)
        assertFalse(state.isPlaying)
        assertEquals(listOf("syncIsPlayingAfterSeek: fixing isPlaying true -> false"), logs)
    }

    private class FakeSessionController : PlaybackSessionController {
        override val hasCurrentMediaItem: Boolean = false
        override val isPlaying: Boolean?
            get() = isPlayingValue

        var isPlayingValue: Boolean? = false
        var duration = 0L
        var canPlaySingle = true
        var played = false
        var paused = false

        override fun playQueue(plan: ControllerQueuePlan) = Unit

        override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) = Unit

        override fun playSingle(song: Song): Boolean = canPlaySingle

        override fun pause() {
            paused = true
        }

        override fun play() {
            played = true
        }

        override fun seekTo(positionMs: Long) = Unit

        override fun currentPositionMs(fallback: Long): Long = fallback

        override fun durationMs(fallback: Long): Long = duration
    }

    private class FakeDurationTracker : PlaybackDurationTracker {
        var paused = false
        var resumed = false

        override fun startPlayback(songId: Int, durationMs: Long, initialPlayedMs: Long) = Unit

        override fun pausePlayback() {
            paused = true
        }

        override fun resumePlayback() {
            resumed = true
        }

        override fun startSeeking() = Unit

        override fun endSeeking() = Unit
    }

    private fun planControllerQueue(queue: PlayQueue, requestedIndex: Int): ControllerQueuePlan? {
        if (queue.isEmpty || requestedIndex !in queue.songs.indices) return null
        return ControllerQueuePlan(queue.songs, requestedIndex)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
