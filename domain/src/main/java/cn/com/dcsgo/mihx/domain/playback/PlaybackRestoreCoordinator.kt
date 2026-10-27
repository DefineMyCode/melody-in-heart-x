package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

data class RestoredPlaybackState(
    val queue: PlayQueue,
    val positionMs: Long,
    val isInfinitePlay: Boolean = false,
    val infinitePlayedSongIds: Set<Int> = emptySet(),
    /** 探索播放会话（2026-10-20）：重启后恢复，保证探索队列自动补充继续生效 */
    val isExplorePlay: Boolean = false,
)

data class PlaybackRestoreResult(
    val queue: PlayQueue,
    val isInfinitePlay: Boolean = false,
    val infinitePlayedSongIds: Set<Int> = emptySet(),
    /** 探索播放会话：重启后恢复（与 [isInfinitePlay] 同路径） */
    val isExplorePlay: Boolean = false,
    val playableSession: RestoredPlayableSession? = null,
)

data class RestoredPlayableSession(
    val song: Song,
    val positionMs: Long,
    val sameNameSongs: List<Song>,
)

/**
 * 播放状态持久化端口。
 *
 * 评审 2026-10-27 · N-1：全部改为 `suspend`。
 *
 * 原实现（`:player` 的 `PlaybackStateStore`）内部用 `runBlocking(Dispatchers.IO)`
 * 把 DataStore 的挂起写包装成同步接口——于是**主线程可达**的路径（ViewModel
 * `onCleared`、用户「清空播放队列」）会同步阻塞在磁盘写上。挂起化后由调用方
 * 决定在哪个线程执行，主线程不再被阻塞。
 */
interface PlaybackStateStorage {
    suspend fun save(
        queue: PlayQueue,
        positionMs: Long,
        isInfinitePlay: Boolean = false,
        infinitePlayedSongIds: Set<Int> = emptySet(),
        isExplorePlay: Boolean = false,
        currentSongId: Int? = null,
    )

    suspend fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long)
    suspend fun clear()
    suspend fun restore(allSongs: List<Song>): RestoredPlaybackState?
}

fun interface PlaybackStateStorageFactory {
    fun create(): PlaybackStateStorage
}

interface PlaybackRestorer {
    suspend fun restore(allSongs: List<Song>): PlaybackRestoreResult?
}

class PlaybackRestoreCoordinator(
    private val restoreState: suspend (allSongs: List<Song>) -> RestoredPlaybackState?,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) : PlaybackRestorer {
    override suspend fun restore(allSongs: List<Song>): PlaybackRestoreResult? {
        val restored = restoreState(allSongs) ?: return null
        val queue = restored.queue
        val currentSong = queue.currentSong
        val playableSession = currentSong
            ?.takeIf(isPlayable)
            ?.let { song ->
                RestoredPlayableSession(
                    song = song,
                    positionMs = restored.positionMs,
                    sameNameSongs = SongVersionManager.sortedSameNameSongs(song, allSongs, isPlayable),
                )
            }

        return PlaybackRestoreResult(
            queue = queue,
            isInfinitePlay = restored.isInfinitePlay,
            infinitePlayedSongIds = restored.infinitePlayedSongIds,
            isExplorePlay = restored.isExplorePlay,
            playableSession = playableSession,
        )
    }
}
