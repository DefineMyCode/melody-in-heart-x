package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

class PlayerPlaybackBridgeFacade(
    private val remainingMediaItems: () -> Int,
    private val pausePlayback: () -> Unit,
    private val resumePlayback: () -> Unit,
    private val currentPlaybackPositionMs: () -> Long,
    private val clearControllerPlaylist: () -> Boolean,
    private val startControllerQueuePlayback: (PlayQueue, Int) -> Boolean,
    private val prepareControllerQueue: (PlayQueue, Int, Long) -> Boolean,
    private val startControllerSinglePlayback: (Song) -> Boolean,
    private val playFromQueue: (PlayQueue, Int) -> Unit,
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val refillInfinitePlayQueue: (Int?) -> Unit,
) {
    fun remainingMediaItems(): Int {
        return remainingMediaItems.invoke()
    }

    fun pausePlayback() {
        pausePlayback.invoke()
    }

    fun resumePlayback() {
        resumePlayback.invoke()
    }

    fun currentPlaybackPositionMs(): Long {
        return currentPlaybackPositionMs.invoke()
    }

    fun clearControllerPlaylist(): Boolean {
        return clearControllerPlaylist.invoke()
    }

    fun startControllerQueuePlayback(queue: PlayQueue, index: Int): Boolean {
        return startControllerQueuePlayback.invoke(queue, index)
    }

    fun prepareControllerQueue(queue: PlayQueue, index: Int, positionMs: Long): Boolean {
        return prepareControllerQueue.invoke(queue, index, positionMs)
    }

    fun startControllerSinglePlayback(song: Song): Boolean {
        return startControllerSinglePlayback.invoke(song)
    }

    fun playFromQueue(queue: PlayQueue, index: Int) {
        playFromQueue.invoke(queue, index)
    }

    fun syncPlayerQueue(queue: PlayQueue) {
        syncPlayerQueue.invoke(queue)
    }

    fun refillInfinitePlayQueue(startedSongId: Int? = null) {
        refillInfinitePlayQueue.invoke(startedSongId)
    }
}
