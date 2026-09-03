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
    private var playbackStart: Triple<Int, Long, Long>? = null
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
        startPlayback = { songId, durationMs, initialPlayedMs -> playbackStart = Triple(songId, durationMs, initialPlayedMs) },
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
            currentPositionMs = 99L,
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
        // 播放位置不再由 controller snapshot 覆盖到 uiState（改由 positionMs 窄流驱动）
        assertEquals(99L, state.currentPositionMs)
        assertEquals(100L, state.durationMs)
        assertEquals(listOf(1, 2), state.sameNameSongs.map { it.id })
        assertEquals(2, trackedSongId)
        assertEquals(2 to 100L, durationUpdate)
        assertEquals(Triple(2, 100L, 42L), playbackStart)
        assertEquals(1, syncedState?.playQueue?.currentIndex)
        // false -> true 的跃变会顺带通知 ticker
        assertEquals(1, isPlayingChanges)
    }

    @Test
    fun syncControllerPlaybackStateNotifiesTickerWhenAdoptingLiveSession() {
        // live session 场景：UI 状态全新（息屏/后台回来、配置变更、ViewModel 重建），
        // 而服务端 ExoPlayer 仍在播放。Media3 只在 isPlaying 变化时回调 onIsPlayingChanged，
        // 新建的 MediaController 注册 listener 时不会补发，只能靠 sync 的跃变通知把
        // 进度 ticker 拉起 —— 缺失这一步进度会恒为 0 而歌曲实际在播。
        val songs = listOf(song(1))
        state = state.copy(
            songs = songs,
            playQueue = PlayQueue().setQueue(songs, startIndex = 0),
            currentSong = songs[0],
            isPlaying = false,
        )

        facade.syncControllerPlaybackState(
            snapshot(
                mediaId = "1",
                isPlaying = true,
                currentPositionMs = 58_993L,
                durationMs = 240_000L,
            )
        )

        assertTrue(state.isPlaying)
        assertEquals(1, isPlayingChanges)
    }

    @Test
    fun syncControllerPlaybackStateNotifiesTickerWhenLiveSessionPauses() {
        val songs = listOf(song(1))
        state = state.copy(
            songs = songs,
            playQueue = PlayQueue().setQueue(songs, startIndex = 0),
            currentSong = songs[0],
            isPlaying = true,
        )

        facade.syncControllerPlaybackState(
            snapshot(
                mediaId = "1",
                isPlaying = false,
                currentPositionMs = 12_000L,
                durationMs = 240_000L,
            )
        )

        assertFalse(state.isPlaying)
        assertEquals(1, isPlayingChanges)
    }

    @Test
    fun syncControllerPlaybackStateDoesNotNotifyTickerWhenPlayingStateUnchanged() {
        // PlaybackController 的 listener.onEvents 把 onPlaybackSnapshot 接成了高频回调，
        // 播放稳态下的重复 sync 不应反复通知 ticker。
        val songs = listOf(song(1))
        state = state.copy(
            songs = songs,
            playQueue = PlayQueue().setQueue(songs, startIndex = 0),
            currentSong = songs[0],
            isPlaying = true,
        )

        repeat(5) { index ->
            facade.syncControllerPlaybackState(
                snapshot(
                    mediaId = "1",
                    isPlaying = true,
                    currentPositionMs = index * 500L,
                    durationMs = 240_000L,
                )
            )
        }

        assertEquals(0, isPlayingChanges)
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
