package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorage
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定 [PlayerPersistenceGraph] 的恢复握手：controllerReady 与 pendingRestore 两个标志
 * 谁先谁后都能汇合；live session 只回填 UI 队列、空会话完整恢复；连接失败兜底放行；
 * 无快照时不恢复。此逻辑历史上是 4 次回归（4fdb2ae/0cce0de/53af2cc/2026-10-06）的聚集地。
 *
 * 用 Dispatchers.Unconfined 让 graph 内部的 launch(io)/withContext(main) 同步执行，
 * 不引入 kotlinx-coroutines-test 依赖。
 */
class PlayerPersistenceGraphTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val unconfined = CoroutineDispatchers(
        main = Dispatchers.Unconfined,
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
    )

    private var state = PlayerUiState()
    private val store = InMemoryPlaybackStateStorage()
    private var liveSession = false
    private var currentPositionMs = 0L
    private var preparedQueue: PlayQueue? = null
    private var preparedIndex: Int? = null
    private var preparedPositionMs: Long? = null
    private val logs = mutableListOf<String>()
    private lateinit var graph: PlayerPersistenceGraph

    private fun createGraph(): PlayerPersistenceGraph {
        graph = PlayerPersistenceGraph(
            playbackStateStorageFactory = PlaybackStateStorageFactory { store },
            scope = scope,
            dispatchers = unconfined,
            state = { state },
            updateState = { transform -> state = transform(state) },
            syncPlaybackState = { },
            currentPlaybackPositionMs = { currentPositionMs },
            prepareControllerQueue = { queue, index, positionMs ->
                preparedQueue = queue
                preparedIndex = index
                preparedPositionMs = positionMs
                true
            },
            hasLiveSession = { liveSession },
            log = { logs += it },
            // JVM 单测无法构造 android.net.Uri，注入不依赖 Uri 的可播判据（sampleRate > 0）
            isPlayable = { it.sampleRate > 0 },
        )
        return graph
    }

    @Test
    fun restoreReadFirstThenControllerReadyAppliesFullRestore() = runBlocking {
        val allSongs = songs(1, 2, 3)
        state = state.copy(songs = allSongs, playQueue = PlayQueue().setQueue(allSongs, startIndex = 0))
        createGraph()
        graph.savePlaybackState(positionMs = 123L)
        state = state.copy(playQueue = PlayQueue(), currentSong = null, isPlaying = true)

        // 恢复读取先完成：pendingRestore 就绪但 controller 未连接，不应应用
        graph.restorePlaybackState()
        assertTrue(state.playQueue.isEmpty)
        assertNull(preparedQueue)

        // controller 连接成功后才决策：空会话 → 完整恢复
        graph.onControllerReady()

        assertEquals(listOf(1, 2, 3), state.playQueue.songs.map { it.id })
        assertEquals(123L, state.currentPositionMs)
        assertEquals(state.playQueue, preparedQueue)
        assertEquals(0, preparedIndex)
        assertEquals(123L, preparedPositionMs)
        assertTrue(logs.any { it.startsWith("restorePlaybackState decision: liveSession=false") })
        assertTrue(logs.any { it.startsWith("Playback state restored: 3 songs") })
    }

    @Test
    fun controllerReadyFirstThenRestoreReadAppliesFullRestore() = runBlocking {
        val allSongs = songs(1, 2, 3)
        state = state.copy(songs = allSongs, playQueue = PlayQueue().setQueue(allSongs, startIndex = 0))
        createGraph()
        graph.savePlaybackState(positionMs = 123L)
        state = state.copy(playQueue = PlayQueue(), currentSong = null, isPlaying = true)

        // controller 先就绪但快照读取未完成：不应应用
        graph.onControllerReady()
        assertTrue(state.playQueue.isEmpty)
        assertNull(preparedQueue)

        // 快照读取完成，双方汇合 → 完整恢复
        graph.restorePlaybackState()

        assertEquals(listOf(1, 2, 3), state.playQueue.songs.map { it.id })
        assertEquals(state.playQueue, preparedQueue)
        assertEquals(123L, preparedPositionMs)
    }

    @Test
    fun liveSessionOnlyFillsUiQueueAndNeverTouchesController() = runBlocking {
        val allSongs = songs(1, 2, 3)
        state = state.copy(songs = allSongs, playQueue = PlayQueue().setQueue(allSongs, startIndex = 0))
        liveSession = true
        createGraph()
        graph.savePlaybackState(positionMs = 123L)
        state = state.copy(
            playQueue = PlayQueue(),
            currentSong = null,
            currentPositionMs = 58_993L,
            isPlaying = true,
        )

        graph.restorePlaybackState()
        graph.onControllerReady()

        // UI 侧队列照常恢复
        assertEquals(listOf(1, 2, 3), state.playQueue.songs.map { it.id })
        // controller 完全不被触动：不重建队列、不写位置、不覆盖播放状态
        assertNull(preparedQueue)
        assertNull(preparedIndex)
        assertNull(preparedPositionMs)
        assertEquals(58_993L, state.currentPositionMs)
        assertTrue(state.isPlaying)
        assertTrue(logs.any { it.startsWith("restorePlaybackState decision: liveSession=true") })
    }

    @Test
    fun connectionFailureFallbackReleasesPendingRestore() = runBlocking {
        val allSongs = songs(1, 2)
        state = state.copy(songs = allSongs, playQueue = PlayQueue().setQueue(allSongs, startIndex = 0))
        createGraph()
        graph.savePlaybackState(positionMs = 10L)
        state = state.copy(playQueue = PlayQueue(), currentSong = null)

        // 快照已读出（pendingRestore 就绪）；连接失败路径同样调用 onControllerReady 放行决策，
        // 否则 pendingRestore 永久悬挂、UI 队列永不恢复
        graph.restorePlaybackState()
        graph.onControllerReady()

        assertEquals(listOf(1, 2), state.playQueue.songs.map { it.id })
        assertEquals(state.playQueue, preparedQueue)
        assertTrue(logs.any { it.startsWith("Playback state restored: 2 songs") })
    }

    @Test
    fun noSnapshotDoesNotApplyAnything() = runBlocking {
        val allSongs = songs(1, 2, 3)
        state = state.copy(songs = allSongs)
        createGraph()

        graph.restorePlaybackState()
        graph.onControllerReady()

        assertTrue(state.playQueue.isEmpty)
        assertNull(preparedQueue)
        assertTrue(logs.any { it.startsWith("restore read: no snapshot to restore") })
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
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
            if (queue.isEmpty && !isInfinitePlay) {
                val existing = saved
                when {
                    existing != null && playbackSongId != null -> {
                        saved = existing.copy(
                            positionMs = positionMs.coerceAtLeast(0L),
                            currentSongId = playbackSongId,
                        )
                    }
                    existing == null && playbackSongId != null -> {
                        saved = SavedPlaybackState(
                            queue = queue,
                            positionMs = positionMs.coerceAtLeast(0L),
                            isInfinitePlay = false,
                            infinitePlayedSongIds = emptySet(),
                            currentSongId = playbackSongId,
                        )
                    }
                    else -> Unit
                }
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
