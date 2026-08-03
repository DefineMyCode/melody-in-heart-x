package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

data class RestoredPlaybackState(
    val queue: PlayQueue,
    val positionMs: Long,
    val isInfinitePlay: Boolean = false,
    val infinitePlayedSongIds: Set<Int> = emptySet(),
)

data class PlaybackRestoreResult(
    val queue: PlayQueue,
    val isInfinitePlay: Boolean = false,
    val infinitePlayedSongIds: Set<Int> = emptySet(),
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
            playableSession = playableSession,
        )
    }
}
