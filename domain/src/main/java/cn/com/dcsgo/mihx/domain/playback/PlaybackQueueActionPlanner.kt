package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

class PlaybackQueueActionPlanner(
    private val playOrderBuilder: QueueManager.PlayOrderBuilder = QueueManager.defaultPlayOrderBuilder,
) {
    data class Plan(
        val queue: PlayQueue,
        val playbackAction: PlaybackAction,
        val savePlaybackState: Boolean = true,
        val clearPlaybackState: Boolean = false,
        val exitInfinitePlay: Boolean = false,
        val nextPlayState: NextPlayState? = null,
        val clearCurrentSong: Boolean = false,
        val clearDuration: Boolean = false,
        val addedCount: Int = 0,
    )

    data class NextPlayState(
        val songId: Int,
        val playModeBeforeNext: PlayMode,
        val skipNextRefill: Boolean,
    )

    sealed class PlaybackAction {
        data class PlayQueueIndex(val index: Int) : PlaybackAction()
        data object SyncQueue : PlaybackAction()
        data object ClearController : PlaybackAction()
        data object None : PlaybackAction()
    }

    fun replaceQueue(
        currentQueue: PlayQueue,
        songs: List<Song>,
        startIndex: Int,
        mode: PlayMode?,
        exitInfinitePlay: Boolean = true,
    ): Plan? {
        if (songs.isEmpty()) return null
        val currentMode = mode ?: currentQueue.playMode
        val newQueue = QueueManager.createQueue(songs, startIndex, currentMode, playOrderBuilder)
        return Plan(
            queue = newQueue,
            playbackAction = PlaybackAction.PlayQueueIndex(startIndex),
            exitInfinitePlay = exitInfinitePlay,
        )
    }

    fun addSong(queue: PlayQueue, song: Song): Plan? {
        val newQueue = QueueManager.addSong(queue, song) ?: return null
        return Plan(
            queue = newQueue,
            playbackAction = if (queue.isEmpty) {
                PlaybackAction.PlayQueueIndex(0)
            } else {
                PlaybackAction.SyncQueue
            },
            addedCount = 1,
        )
    }

    fun addSongs(queue: PlayQueue, songs: List<Song>): Plan? {
        val (newQueue, addedCount) = QueueManager.addSongs(queue, songs) ?: return null
        return Plan(
            queue = newQueue,
            playbackAction = if (queue.isEmpty && newQueue.songs.isNotEmpty()) {
                PlaybackAction.PlayQueueIndex(0)
            } else {
                PlaybackAction.SyncQueue
            },
            addedCount = addedCount,
        )
    }

    fun addSongAsNext(
        queue: PlayQueue,
        song: Song,
        playModeBeforeNext: PlayMode?,
        isInfinitePlay: Boolean,
    ): Plan {
        val modeToRestore = playModeBeforeNext ?: queue.playMode
        val newQueue = QueueManager.addSongAsNext(queue, song)
        return Plan(
            queue = newQueue,
            playbackAction = if (queue.isEmpty) {
                PlaybackAction.PlayQueueIndex(0)
            } else {
                PlaybackAction.SyncQueue
            },
            nextPlayState = NextPlayState(
                songId = song.id,
                playModeBeforeNext = modeToRestore,
                skipNextRefill = isInfinitePlay,
            ),
        )
    }

    fun addSongsAsNext(
        queue: PlayQueue,
        songs: List<Song>,
        playModeBeforeNext: PlayMode?,
        isInfinitePlay: Boolean,
    ): Plan? {
        val modeToRestore = playModeBeforeNext ?: queue.playMode
        val (newQueue, addedCount) = QueueManager.addSongsAsNext(queue, songs) ?: return null
        val nextSong = newQueue.songs.getOrNull(newQueue.currentIndex + 1)
            ?: newQueue.currentSong
            ?: return null
        return Plan(
            queue = newQueue,
            playbackAction = if (queue.isEmpty) {
                PlaybackAction.PlayQueueIndex(0)
            } else {
                PlaybackAction.SyncQueue
            },
            nextPlayState = NextPlayState(
                songId = nextSong.id,
                playModeBeforeNext = modeToRestore,
                skipNextRefill = isInfinitePlay,
            ),
            addedCount = addedCount,
        )
    }

    fun removeSong(queue: PlayQueue, songId: Int): Plan {
        val wasCurrentSong = queue.currentSong?.id == songId
        val newQueue = QueueManager.removeSong(queue, songId)
        return Plan(
            queue = newQueue,
            playbackAction = when {
                !wasCurrentSong -> PlaybackAction.SyncQueue
                newQueue.isEmpty -> PlaybackAction.ClearController
                else -> PlaybackAction.PlayQueueIndex(newQueue.currentIndex)
            },
            clearCurrentSong = wasCurrentSong && newQueue.isEmpty,
        )
    }

    fun removeSongAt(queue: PlayQueue, index: Int): Plan {
        val wasCurrentSong = queue.currentIndex == index
        val newQueue = QueueManager.removeSongAt(queue, index)
        return Plan(
            queue = newQueue,
            playbackAction = when {
                !wasCurrentSong -> PlaybackAction.SyncQueue
                newQueue.isEmpty -> PlaybackAction.ClearController
                else -> PlaybackAction.PlayQueueIndex(newQueue.currentIndex)
            },
            clearCurrentSong = wasCurrentSong && newQueue.isEmpty,
        )
    }

    fun clearQueue(): Plan {
        return Plan(
            queue = PlayQueue(),
            playbackAction = PlaybackAction.ClearController,
            savePlaybackState = false,
            clearPlaybackState = true,
            clearCurrentSong = true,
            clearDuration = true,
        )
    }

    fun togglePlayMode(queue: PlayQueue): Plan {
        return setPlayMode(queue, queue.playMode.next())
    }

    fun setPlayMode(queue: PlayQueue, mode: PlayMode): Plan {
        return Plan(
            queue = QueueManager.setPlayMode(queue, mode, playOrderBuilder),
            playbackAction = PlaybackAction.SyncQueue,
        )
    }

    fun playQueueItem(queue: PlayQueue, index: Int): Plan? {
        if (index !in queue.songs.indices) return null
        val newQueue = QueueManager.withCurrentIndex(queue, index, playOrderBuilder)
        return Plan(
            queue = newQueue,
            playbackAction = PlaybackAction.PlayQueueIndex(index),
        )
    }
}
