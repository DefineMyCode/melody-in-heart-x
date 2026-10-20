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

interface PlaybackStateStorage {
    fun save(
        queue: PlayQueue,
        positionMs: Long,
        isInfinitePlay: Boolean = false,
        infinitePlayedSongIds: Set<Int> = emptySet(),
        isExplorePlay: Boolean = false,
        currentSongId: Int? = null,
    )

    fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long)
    fun clear()
    fun restore(allSongs: List<Song>): RestoredPlaybackState?
}

fun interface PlaybackStateStorageFactory {
    fun create(): PlaybackStateStorage
}

interface PlaybackRestorer {
    fun restore(allSongs: List<Song>): PlaybackRestoreResult?
}

class PlaybackRestoreCoordinator(
    private val restoreState: (allSongs: List<Song>) -> RestoredPlaybackState?,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) : PlaybackRestorer {
    override fun restore(allSongs: List<Song>): PlaybackRestoreResult? {
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
