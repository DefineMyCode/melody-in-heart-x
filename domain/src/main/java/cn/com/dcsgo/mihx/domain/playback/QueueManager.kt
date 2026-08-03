package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

object QueueManager {
    data class PlayModeRestorePlan(val queue: PlayQueue)

    fun interface PlayOrderBuilder {
        fun build(songs: List<Song>, startIndex: Int, mode: PlayMode): List<Int>
    }

    val defaultPlayOrderBuilder = PlayOrderBuilder { songs, startIndex, mode ->
        PlayQueue.buildPlayOrderIds(songs, startIndex, mode)
    }

    fun createQueue(
        songs: List<Song>,
        startIndex: Int,
        mode: PlayMode,
        playOrderBuilder: PlayOrderBuilder = defaultPlayOrderBuilder,
    ): PlayQueue {
        val queue = PlayQueue().setQueue(songs, startIndex, mode)
        return queue.withPlayOrder(playOrderBuilder, startIndex)
    }

    fun addSong(queue: PlayQueue, song: Song): PlayQueue? {
        if (queue.songs.any { it.id == song.id }) return null
        return queue.addSong(song)
    }

    fun addSongs(queue: PlayQueue, songs: List<Song>): Pair<PlayQueue, Int>? {
        if (songs.isEmpty()) return null
        return queue.addSongs(songs) to songs.size
    }

    fun addSongAsNext(queue: PlayQueue, song: Song): PlayQueue {
        return queue.addSongAsNext(song).setPlayMode(PlayMode.SEQUENTIAL)
    }

    fun addSongsAsNext(queue: PlayQueue, songs: List<Song>): Pair<PlayQueue, Int>? {
        return queue.addSongsAsNext(songs)
    }

    fun removeSong(queue: PlayQueue, songId: Int): PlayQueue {
        return queue.removeSong(songId)
    }

    fun removeSongAt(queue: PlayQueue, index: Int): PlayQueue {
        return queue.removeSongAt(index)
    }

    fun setPlayMode(
        queue: PlayQueue,
        mode: PlayMode,
        playOrderBuilder: PlayOrderBuilder = defaultPlayOrderBuilder,
    ): PlayQueue {
        return queue.setPlayMode(mode).withPlayOrder(playOrderBuilder)
    }

    fun withCurrentIndex(
        queue: PlayQueue,
        index: Int,
        playOrderBuilder: PlayOrderBuilder = defaultPlayOrderBuilder,
    ): PlayQueue {
        return queue.withCurrentIndex(index).withPlayOrder(playOrderBuilder)
    }

    fun restorePlayModeAfterNextSong(
        queue: PlayQueue,
        currentSongId: Int?,
        nextPlaySongId: Int?,
        playModeBeforeNext: PlayMode?,
        startedSongId: Int?,
        playOrderBuilder: PlayOrderBuilder = defaultPlayOrderBuilder,
    ): PlayModeRestorePlan? {
        if (
            nextPlaySongId == null ||
            playModeBeforeNext == null ||
            currentSongId != nextPlaySongId
        ) {
            return null
        }

        val currentQueueIndex = startedSongId
            ?.let { songId -> queue.songs.indexOfFirst { it.id == songId } }
            ?.takeIf { it >= 0 }
            ?: queue.currentIndex

        return PlayModeRestorePlan(
            queue = queue.copy(currentIndex = currentQueueIndex)
                .setPlayMode(playModeBeforeNext)
                .withPlayOrder(playOrderBuilder, currentQueueIndex),
        )
    }

    private fun PlayQueue.withPlayOrder(
        playOrderBuilder: PlayOrderBuilder,
        startIndex: Int = currentIndex,
    ): PlayQueue {
        if (songs.isEmpty()) return copy(playOrderIds = emptyList())
        return copy(playOrderIds = playOrderBuilder.build(songs, startIndex, playMode))
    }
}
