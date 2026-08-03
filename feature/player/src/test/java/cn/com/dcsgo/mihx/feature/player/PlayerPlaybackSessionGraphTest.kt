package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationTracker
import cn.com.dcsgo.mihx.domain.playback.PlaybackSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackSessionGraphTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var state = PlayerUiState()
    private val controller = FakeSessionController()
    private val tracker = FakeDurationTracker()
    private var trackedSongId: Int? = null
    private var sameNameSongId: Int? = null
    private var saved = false
    private val graph = PlayerPlaybackSessionGraph(
        scope = scope,
        controller = controller,
        durationTracker = tracker,
        state = { state },
        updateState = { transform -> state = transform(state) },
        setTrackedSongId = { trackedSongId = it },
        updateSameNameSongs = { sameNameSongId = it.id },
        savePlaybackState = { saved = true },
        planControllerQueue = { queue, index -> ControllerQueuePlan(queue.songs, index) },
    )

    @Test
    fun startSinglePlaybackUpdatesUiAndTracking() {
        controller.duration = 250L

        val started = graph.startSinglePlayback(song(2))

        assertTrue(started)
        assertTrue(controller.playedSingle)
        assertEquals(2, state.currentSong?.id)
        assertTrue(state.isPlaying)
        assertEquals(250L, state.durationMs)
        assertEquals(2, trackedSongId)
        assertEquals(2, sameNameSongId)
        assertEquals(2, tracker.startedSongId)
    }

    @Test
    fun pausePlaybackUpdatesStateAndRequestsSave() {
        state = state.copy(isPlaying = true)

        graph.pausePlayback()

        assertTrue(controller.paused)
        assertTrue(tracker.paused)
        assertFalse(state.isPlaying)
        assertTrue(saved)
    }

    private class FakeSessionController : PlaybackSessionController {
        override val hasCurrentMediaItem: Boolean = false
        override val isPlaying: Boolean?
            get() = isPlayingValue

        var isPlayingValue: Boolean? = false
        var duration = 0L
        var playedQueue = false
        var playedSingle = false
        var paused = false
        var seekPosition: Long? = null

        override fun playQueue(plan: ControllerQueuePlan) {
            playedQueue = true
        }

        override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) = Unit

        override fun playSingle(song: Song): Boolean {
            playedSingle = true
            return true
        }

        override fun pause() {
            paused = true
        }

        override fun play() = Unit

        override fun seekTo(positionMs: Long) {
            seekPosition = positionMs
        }

        override fun currentPositionMs(fallback: Long): Long = fallback

        override fun durationMs(fallback: Long): Long = duration
    }

    private class FakeDurationTracker : PlaybackDurationTracker {
        var startedSongId: Int? = null
        var paused = false

        override fun startPlayback(songId: Int, durationMs: Long) {
            startedSongId = songId
        }

        override fun pausePlayback() {
            paused = true
        }

        override fun resumePlayback() = Unit

        override fun startSeeking() = Unit

        override fun endSeeking() = Unit
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
