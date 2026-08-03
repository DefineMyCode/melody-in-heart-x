package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionCoordinatorTest {

    private val controller = FakeSessionController()
    private val tracker = FakeDurationTracker()
    private val coordinator = PlaybackSessionCoordinator(
        controller = controller,
        durationTracker = tracker,
        planControllerQueue = { queue, index ->
            queue.songs.getOrNull(index)?.let { ControllerQueuePlan(queue.songs, index) }
        },
    )

    @Test
    fun startQueuePlaybackStartsControllerAndTracking() {
        controller.duration = 123L
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 1)

        val result = coordinator.startQueuePlayback(queue, index = 1)

        assertTrue(controller.playedQueue)
        assertEquals(1, controller.lastQueuePlan?.startIndex)
        assertEquals(listOf(1, 2), controller.lastQueuePlan?.songs?.map { it.id })
        assertEquals(2, tracker.startedSongId)
        assertEquals(123L, tracker.startedDuration)
        assertEquals(2, result?.trackedSongId)
        assertEquals(2, result?.stateUpdate?.currentSong?.id)
        assertEquals(true, result?.stateUpdate?.isPlaying)
        assertEquals(0L, result?.stateUpdate?.currentPositionMs)
        assertEquals(123L, result?.stateUpdate?.durationMs)
    }

    @Test
    fun startQueuePlaybackReturnsNullForInvalidIndex() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        val result = coordinator.startQueuePlayback(queue, index = 4)

        assertNull(result)
        assertFalse(controller.playedQueue)
        assertNull(tracker.startedSongId)
    }

    @Test
    fun startSinglePlaybackReturnsNullWhenControllerRejectsSong() {
        controller.canPlaySingle = false

        val result = coordinator.startSinglePlayback(song(1))

        assertNull(result)
        assertNull(tracker.startedSongId)
    }

    @Test
    fun pauseWhenPlayingPausesTrackerAndRequestsSave() {
        val result = coordinator.pause(currentIsPlaying = true)

        assertTrue(controller.paused)
        assertTrue(tracker.paused)
        assertEquals(false, result.stateUpdate?.isPlaying)
        assertTrue(result.shouldSavePlaybackState)
    }

    @Test
    fun pauseWhenAlreadyPausedDoesNotRequestSave() {
        val result = coordinator.pause(currentIsPlaying = false)

        assertTrue(controller.paused)
        assertFalse(tracker.paused)
        assertNull(result.stateUpdate)
        assertFalse(result.shouldSavePlaybackState)
    }

    @Test
    fun resumeWithCurrentSongResumesTrackerAndState() {
        val result = coordinator.resume(currentIsPlaying = false, hasCurrentSong = true)

        assertTrue(controller.played)
        assertTrue(tracker.resumed)
        assertEquals(true, result.stateUpdate?.isPlaying)
    }

    @Test
    fun seekDelegatesToControllerAndTracker() {
        coordinator.startSeeking()
        coordinator.seekTo(42L)
        coordinator.endSeeking()

        assertTrue(tracker.startedSeeking)
        assertEquals(42L, controller.seekPosition)
        assertTrue(tracker.endedSeeking)
    }

    private class FakeSessionController : PlaybackSessionController {
        override val hasCurrentMediaItem: Boolean = false
        override val isPlaying: Boolean? = false
        var duration = 0L
        var canPlaySingle = true
        var playedQueue = false
        var played = false
        var paused = false
        var lastQueuePlan: ControllerQueuePlan? = null
        var seekPosition: Long? = null

        override fun playQueue(plan: ControllerQueuePlan) {
            playedQueue = true
            lastQueuePlan = plan
        }

        override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) = Unit

        override fun playSingle(song: Song): Boolean = canPlaySingle

        override fun pause() {
            paused = true
        }

        override fun play() {
            played = true
        }

        override fun seekTo(positionMs: Long) {
            seekPosition = positionMs
        }

        override fun currentPositionMs(fallback: Long): Long = fallback

        override fun durationMs(fallback: Long): Long = duration
    }

    private class FakeDurationTracker : PlaybackDurationTracker {
        var startedSongId: Int? = null
        var startedDuration: Long? = null
        var paused = false
        var resumed = false
        var startedSeeking = false
        var endedSeeking = false

        override fun startPlayback(songId: Int, durationMs: Long) {
            startedSongId = songId
            startedDuration = durationMs
        }

        override fun pausePlayback() {
            paused = true
        }

        override fun resumePlayback() {
            resumed = true
        }

        override fun startSeeking() {
            startedSeeking = true
        }

        override fun endSeeking() {
            endedSeeking = true
        }
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
