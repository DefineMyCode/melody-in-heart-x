package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackState
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerStateFacadeTest {

    private var state = PlayerUiState()
    private var trackedSongId: Int? = null
    private var durationUpdate: Pair<Int, Long>? = null
    private var playbackStart: Pair<Int, Long>? = null
    private var pausedTracking = false
    private var resumedTracking = false
    private var saved = false
    private var isPlayingChanges = 0
    private var syncedState: ControllerPlaybackState? = null
    private val facade = PlayerControllerStateFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        synchronizer = ControllerPlaybackStateSynchronizer(
            sameNameSongs = { song, songs -> songs.filter { it.groupKey == song.groupKey } },
        ),
        trackedSongId = { trackedSongId },
        setTrackedSongId = { trackedSongId = it },
        updateDuration = { songId, durationMs -> durationUpdate = songId to durationMs },
        startPlayback = { songId, durationMs -> playbackStart = songId to durationMs },
        pausePlaybackTracking = { pausedTracking = true },
        resumePlaybackTracking = { resumedTracking = true },
        savePlaybackState = { saved = true },
        onIsPlayingChanged = { isPlayingChanges++ },
        onControllerPlaybackSynced = { syncedState = it },
    )

    @Test
    fun syncControllerPlaybackStateUpdatesUiAndTracking() {
        val songs = listOf(
            song(1, title = "Song"),
            song(2, title = "Song"),
            song(3, title = "Other"),
        )
        state = state.copy(
            songs = songs,
            playQueue = PlayQueue().setQueue(songs, startIndex = 0),
            currentSong = songs[0],
        )

        facade.syncControllerPlaybackState(
            snapshot(
                mediaId = "2",
                isPlaying = true,
                currentPositionMs = 42L,
                durationMs = 100L,
            )
        )

        assertEquals(2, state.currentSong?.id)
        assertEquals(1, state.playQueue.currentIndex)
        assertTrue(state.isPlaying)
        assertEquals(42L, state.currentPositionMs)
        assertEquals(100L, state.durationMs)
        assertEquals(listOf(1, 2), state.sameNameSongs.map { it.id })
        assertEquals(2, trackedSongId)
        assertEquals(2 to 100L, durationUpdate)
        assertEquals(2 to 100L, playbackStart)
        assertEquals(1, syncedState?.playQueue?.currentIndex)
    }

    @Test
    fun handleIsPlayingChangedPausesTrackingAndSavesOnRealPause() {
        state = state.copy(currentSong = song(1), isPlaying = true)

        facade.handleControllerIsPlayingChanged(isPlaying = false, isBuffering = false)

        assertFalse(state.isPlaying)
        assertTrue(pausedTracking)
        assertTrue(saved)
        assertEquals(1, isPlayingChanges)
    }

    @Test
    fun handleIsPlayingChangedResumesTrackingWhenPlaybackStarts() {
        state = state.copy(currentSong = song(1), isPlaying = false)

        facade.handleControllerIsPlayingChanged(isPlaying = true, isBuffering = false)

        assertTrue(state.isPlaying)
        assertTrue(resumedTracking)
        assertEquals(1, isPlayingChanges)
    }

    @Test
    fun handleIsPlayingChangedDoesNotPauseTrackingWhileBuffering() {
        state = state.copy(currentSong = song(1), isPlaying = true)

        facade.handleControllerIsPlayingChanged(isPlaying = false, isBuffering = true)

        assertFalse(state.isPlaying)
        assertFalse(pausedTracking)
        assertFalse(saved)
    }

    private fun snapshot(
        mediaId: String?,
        isPlaying: Boolean,
        currentPositionMs: Long,
        durationMs: Long?,
    ): ControllerPlaybackSnapshot {
        return ControllerPlaybackSnapshot(
            mediaId = mediaId,
            isPlaying = isPlaying,
            isBuffering = false,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
        )
    }

    private fun song(
        id: Int,
        title: String = "Song $id",
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
