package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinator
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorage
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPersistenceFacadeTest {

    private var state = PlayerUiState()
    private val store = InMemoryPlaybackStateStorage()
    private var currentPositionMs = 0L
    private var preparedQueue: PlayQueue? = null
    private var preparedIndex: Int? = null
    private var preparedPositionMs: Long? = null
    private val launchedTasks = mutableListOf<() -> Unit>()
    private val logs = mutableListOf<String>()
    private val facade = PlayerPersistenceFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        playbackStateStore = store,
        playbackRestoreCoordinator = PlaybackRestoreCoordinator(
            restoreState = store::restore,
            isPlayable = { it.sampleRate > 0 },
        ),
        currentPlaybackPositionMs = { currentPositionMs },
        prepareControllerQueue = { queue, index, positionMs ->
            preparedQueue = queue
            preparedIndex = index
            preparedPositionMs = positionMs
            true
        },
        launchIo = { task -> launchedTasks += task },
        log = { logs += it },
    )

    @Test
    fun savePlaybackStateAsyncCapturesCurrentPositionBeforeLaunching() {
        currentPositionMs = 42L
        state = state.copy(playQueue = PlayQueue().setQueue(songs(1), startIndex = 0))

        facade.savePlaybackStateAsync()
        currentPositionMs = 99L
        launchedTasks.single().invoke()

        val restored = store.restore(songs(1))
        assertEquals(42L, restored?.positionMs)
    }

    @Test
    fun clearPlaybackStateRemovesSavedQueue() {
        state = state.copy(playQueue = PlayQueue().setQueue(songs(1), startIndex = 0))
        facade.savePlaybackState(positionMs = 10L)

        facade.clearPlaybackState()

        assertEquals(null, store.restore(songs(1)))
    }

    @Test
    fun restorePlaybackStateAppliesQueueAndPreparedSession() {
        val allSongs = listOf(
            song(1, title = "Song", sampleRate = 44_100),
            song(2, title = "Song", sampleRate = 96_000),
        )
        val queue = PlayQueue().setQueue(allSongs, startIndex = 0)
        state = state.copy(songs = allSongs, playQueue = queue)
        facade.savePlaybackState(positionMs = 123L)
        state = state.copy(playQueue = PlayQueue(), currentSong = null, isPlaying = true)

        val result = facade.restorePlaybackState()
        assertNotNull(result)
        facade.applyRestoreResult(result!!)

        assertEquals(listOf(1, 2), state.playQueue.songs.map { it.id })
        assertEquals(1, state.currentSong?.id)
        assertEquals(123L, state.currentPositionMs)
        assertFalse(state.isPlaying)
        assertEquals(listOf(2, 1), state.sameNameSongs.map { it.id })
        assertEquals(state.playQueue, preparedQueue)
        assertEquals(0, preparedIndex)
        assertEquals(123L, preparedPositionMs)
        assertTrue(logs.single().startsWith("Playback state restored: 2 songs"))
    }

    @Test
    fun restorePlaybackStateAppliesInfinitePlayState() {
        val allSongs = songs(1, 2, 3)
        val queue = PlayQueue().setQueue(allSongs.take(2), startIndex = 0)
        state = state.copy(
            songs = allSongs,
            playQueue = queue,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2),
        )
        facade.savePlaybackState(positionMs = 123L)
        state = state.copy(
            playQueue = PlayQueue(),
            isInfinitePlay = false,
            infinitePlayedSongIds = emptySet(),
        )

        val result = facade.restorePlaybackState()
        assertNotNull(result)
        facade.applyRestoreResult(result!!)

        assertTrue(state.isInfinitePlay)
        assertEquals(setOf(1, 2), state.infinitePlayedSongIds)
    }

    @Test
    fun savePlaybackStatePersistsCurrentSongEvenWhenQueueIndexIsStale() {
        val allSongs = songs(1, 2)
        state = state.copy(
            songs = allSongs,
            playQueue = PlayQueue().setQueue(allSongs, startIndex = 1),
            currentSong = allSongs[0],
        )

        facade.savePlaybackState(positionMs = 60_000L)

        val restored = store.restore(allSongs)
        assertEquals(0, restored?.queue?.currentIndex)
        assertEquals(1, restored?.queue?.currentSong?.id)
        assertEquals(60_000L, restored?.positionMs)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int = 44_100,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = sampleRate,
        )
    }

    private class InMemoryPlaybackStateStorage : PlaybackStateStorage {
        private var saved: SavedPlaybackState? = null

        override fun save(
            queue: PlayQueue,
            positionMs: Long,
            isInfinitePlay: Boolean,
            infinitePlayedSongIds: Set<Int>,
            currentSongId: Int?,
        ) {
            val playbackSongId = currentSongId ?: queue.currentSong?.id
            if (queue.isEmpty && !isInfinitePlay && playbackSongId == null) {
                clear()
                return
            }
            saved = SavedPlaybackState(
                queue = queue,
                positionMs = positionMs.coerceAtLeast(0L),
                isInfinitePlay = isInfinitePlay,
                infinitePlayedSongIds = infinitePlayedSongIds,
                currentSongId = playbackSongId,
            )
        }

        override fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long) {
            saved = SavedPlaybackState(
                queue = saved?.queue ?: PlayQueue(),
                positionMs = positionMs.coerceAtLeast(0L),
                isInfinitePlay = saved?.isInfinitePlay ?: false,
                infinitePlayedSongIds = saved?.infinitePlayedSongIds ?: emptySet(),
                currentSongId = songId,
            )
        }

        override fun clear() {
            saved = null
        }

        override fun restore(allSongs: List<Song>): RestoredPlaybackState? {
            val snapshot = saved ?: return null
            val availableSongs = allSongs.associateBy { it.id }
            val queueSongs = snapshot.queue.songs.mapNotNull { availableSongs[it.id] }
            val queue = if (queueSongs.isEmpty()) {
                PlayQueue(playMode = snapshot.queue.playMode)
            } else {
                PlayQueue()
                    .setQueue(
                        queueSongs,
                        snapshot.queue.currentIndex.coerceIn(0, queueSongs.lastIndex),
                        snapshot.queue.playMode,
                    )
                    .withCurrentSongId(snapshot.currentSongId, allSongs)
            }
            return RestoredPlaybackState(
                queue = queue,
                positionMs = snapshot.positionMs,
                isInfinitePlay = snapshot.isInfinitePlay,
                infinitePlayedSongIds = snapshot.infinitePlayedSongIds.filter { it in availableSongs }.toSet(),
            )
        }

        private fun PlayQueue.withCurrentSongId(songId: Int?, allSongs: List<Song>): PlayQueue {
            if (songId == null || currentSong?.id == songId) return this
            val queueIndex = songs.indexOfFirst { it.id == songId }
            if (queueIndex >= 0) return copy(currentIndex = queueIndex)
            val song = allSongs.firstOrNull { it.id == songId } ?: return this
            return PlayQueue().setQueue(listOf(song), startIndex = 0, mode = playMode)
        }

        private data class SavedPlaybackState(
            val queue: PlayQueue,
            val positionMs: Long,
            val isInfinitePlay: Boolean,
            val infinitePlayedSongIds: Set<Int>,
            val currentSongId: Int?,
        )
    }
}
