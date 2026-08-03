package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerPlaybackStateSynchronizerTest {

    private val synchronizer = ControllerPlaybackStateSynchronizer(
        sameNameSongs = { song, songs -> songs.filter { it.groupKey == song.groupKey } },
    )

    @Test
    fun syncUsesControllerSongAndUpdatesQueueIndex() {
        val state = playbackState(
            songs = songs(1, 2, 3),
            queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0),
            currentSong = song(1),
        )

        val result = synchronizer.sync(
            current = state,
            snapshot = snapshot(mediaId = "3", isPlaying = true, position = 42L, duration = 180_000L),
            trackedSongId = null,
        )

        assertEquals(3, result.state.currentSong?.id)
        assertEquals(2, result.state.playQueue.currentIndex)
        assertEquals(42L, result.state.currentPositionMs)
        assertEquals(180_000L, result.state.durationMs)
        assertEquals(3, result.playbackStart?.songId)
        assertEquals(3, result.durationUpdate?.songId)
        assertEquals(3, result.trackedSongId)
    }

    @Test
    fun syncKeepsCurrentSongWhenMediaIdIsUnknown() {
        val currentSong = song(1)
        val state = playbackState(
            songs = songs(1),
            queue = PlayQueue().setQueue(songs(1), startIndex = 0),
            currentSong = currentSong,
            duration = 100L,
        )

        val result = synchronizer.sync(
            current = state,
            snapshot = snapshot(mediaId = "missing", isPlaying = false, position = -5L, duration = null),
            trackedSongId = 1,
        )

        assertEquals(currentSong, result.state.currentSong)
        assertEquals(0L, result.state.currentPositionMs)
        assertEquals(100L, result.state.durationMs)
        assertNull(result.durationUpdate)
        assertNull(result.playbackStart)
    }

    @Test
    fun syncDoesNotRestartTrackingForAlreadyTrackedSong() {
        val state = playbackState(
            songs = songs(1),
            queue = PlayQueue().setQueue(songs(1), startIndex = 0),
        )

        val result = synchronizer.sync(
            current = state,
            snapshot = snapshot(mediaId = "1", isPlaying = true, position = 0L, duration = 100L),
            trackedSongId = 1,
        )

        assertNull(result.playbackStart)
        assertEquals(1, result.trackedSongId)
    }

    @Test
    fun isPlayingTransitionIgnoresBufferingPause() {
        val bufferingPause = synchronizer.isPlayingTransition(
            previousIsPlaying = true,
            newIsPlaying = false,
            isBuffering = true,
            hasCurrentSong = true,
        )
        val realPause = synchronizer.isPlayingTransition(
            previousIsPlaying = true,
            newIsPlaying = false,
            isBuffering = false,
            hasCurrentSong = true,
        )
        val resume = synchronizer.isPlayingTransition(
            previousIsPlaying = false,
            newIsPlaying = true,
            isBuffering = false,
            hasCurrentSong = true,
        )

        assertFalse(bufferingPause.shouldPauseTracking)
        assertTrue(realPause.shouldPauseTracking)
        assertTrue(resume.shouldResumeTracking)
    }

    @Test
    fun queueManagerRestoresPlayModeAfterNextSong() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1, mode = PlayMode.SEQUENTIAL)

        val plan = QueueManager.restorePlayModeAfterNextSong(
            queue = queue,
            currentSongId = 2,
            nextPlaySongId = 2,
            playModeBeforeNext = PlayMode.SHUFFLE,
            startedSongId = 3,
        )

        assertEquals(2, plan?.queue?.currentIndex)
        assertEquals(PlayMode.SHUFFLE, plan?.queue?.playMode)
    }

    @Test
    fun queueManagerRestoreUsesInjectedPlayOrderBuilder() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1, mode = PlayMode.SEQUENTIAL)

        val plan = QueueManager.restorePlayModeAfterNextSong(
            queue = queue,
            currentSongId = 2,
            nextPlaySongId = 2,
            playModeBeforeNext = PlayMode.SHUFFLE,
            startedSongId = 3,
            playOrderBuilder = QueueManager.PlayOrderBuilder { _, _, _ -> listOf(3, 1, 2) },
        )

        assertEquals(listOf(3, 1, 2), plan?.queue?.currentPlayOrderIds())
    }

    @Test
    fun queueManagerDoesNotRestoreForUnrelatedSong() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        val plan = QueueManager.restorePlayModeAfterNextSong(
            queue = queue,
            currentSongId = 1,
            nextPlaySongId = 2,
            playModeBeforeNext = PlayMode.SHUFFLE,
            startedSongId = null,
        )

        assertNull(plan)
    }

    private fun playbackState(
        songs: List<Song>,
        queue: PlayQueue,
        currentSong: Song? = null,
        duration: Long = 0L,
    ): ControllerPlaybackState {
        return ControllerPlaybackState(
            songs = songs,
            playQueue = queue,
            currentSong = currentSong,
            isPlaying = false,
            currentPositionMs = 0L,
            durationMs = duration,
            sameNameSongs = emptyList(),
        )
    }

    private fun snapshot(
        mediaId: String?,
        isPlaying: Boolean,
        position: Long,
        duration: Long?,
    ): ControllerPlaybackSnapshot {
        return ControllerPlaybackSnapshot(
            mediaId = mediaId,
            isPlaying = isPlaying,
            isBuffering = false,
            currentPositionMs = position,
            durationMs = duration,
        )
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int, title: String = "Song $id"): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
