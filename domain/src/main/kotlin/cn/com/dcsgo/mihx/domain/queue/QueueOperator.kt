package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import javax.inject.Inject

/**
 * Pure operations that transform a [PlayQueue] into a new [PlayQueue] (immutable style). All
 * operations preserve queue-index integrity and never dedupe by [Song.id] (gate A5).
 *
 * Invariant: [PlayQueue.playOrderIds] is the ordered list of [Song.id]s that defines play order;
 * [PlayQueue.songs] is the content list. Operations keep both consistent.
 */
class QueueOperator @Inject constructor(
    private val uniformRandomPlanner: UniformRandomPlanner,
    private val randomQueuePlanner: RandomQueuePlanner,
) {

    /**
     * Inserts [songs] as the next block right after [anchorIndex] (the current item position in
     * the play-order sequence). Duplicate ids within [songs] are allowed; the current item stays
     * at [anchorIndex] and the inserted songs become the following positions.
     */
    fun addSongAsNext(queue: PlayQueue, songs: List<Song>, anchorIndex: Int): PlayQueue {
        if (songs.isEmpty()) return queue
        val anchor = anchorIndex.coerceIn(0, queue.playOrderIds.lastIndex.coerceAtLeast(0))
        val newOrderIds = buildList {
            addAll(queue.playOrderIds.subList(0, anchor + 1))
            addAll(songs.map { it.id })
            if (anchor + 1 < queue.playOrderIds.size) {
                addAll(queue.playOrderIds.subList(anchor + 1, queue.playOrderIds.size))
            }
        }
        val newSongs = queue.songs.toMutableList()
        for (s in songs) {
            if (newSongs.none { it.id == s.id }) newSongs.add(s)
        }
        return queue.copy(songs = newSongs, playOrderIds = newOrderIds, currentIndex = anchor)
    }

    /**
     * Appends [songs] to the tail. When [allowDuplicates] is false, incoming songs already present
     * in the queue are skipped (this filters the *incoming* list, never collapses the existing
     * queue — gate A5). When true, every incoming song is appended as its own position.
     */
    fun addSongsToTail(queue: PlayQueue, songs: List<Song>, allowDuplicates: Boolean): PlayQueue {
        if (songs.isEmpty()) return queue
        val toAdd = if (allowDuplicates) {
            songs
        } else {
            songs.filter { s -> queue.playOrderIds.none { id -> id == s.id } }
        }
        if (toAdd.isEmpty()) return queue
        val newOrderIds = queue.playOrderIds + toAdd.map { it.id }
        val newSongs = queue.songs.toMutableList()
        for (s in toAdd) {
            if (newSongs.none { it.id == s.id }) newSongs.add(s)
        }
        return queue.copy(songs = newSongs, playOrderIds = newOrderIds)
    }

    /**
     * Switches play mode, rebuilding [PlayQueue.playOrderIds]. RANDOM goes through
     * [UniformRandomPlanner] (weighted by [playCounts]); the currently-playing item is kept as the
     * current position in the new order.
     */
    fun switchPlayMode(queue: PlayQueue, mode: PlayMode, playCounts: Map<Long, Long> = emptyMap()): PlayQueue {
        val newOrderIds = when (mode) {
            PlayMode.SEQUENTIAL -> queue.songs.map { it.id }
            PlayMode.REVERSE -> queue.songs.map { it.id }.asReversed()
            PlayMode.RANDOM -> uniformRandomPlanner.plan(queue.songs.map { it.id }, playCounts)
        }
        val currentId = queue.playOrderIds.getOrNull(queue.currentIndex)
        val newCurrentIndex = if (currentId != null) {
            newOrderIds.indexOf(currentId).coerceAtLeast(0)
        } else {
            queue.currentIndex.coerceIn(0, newOrderIds.lastIndex.coerceAtLeast(0))
        }
        return queue.copy(playOrderIds = newOrderIds, playMode = mode, currentIndex = newCurrentIndex)
    }

    /**
     * Removes the queue item at [queueIndex] (an index into the play-order sequence). The
     * underlying [Song] is dropped from content only when no remaining order id references it.
     */
    fun removeAt(queue: PlayQueue, queueIndex: Int): PlayQueue {
        val orderSize = queue.playOrderIds.size
        if (queueIndex < 0 || queueIndex >= orderSize) return queue
        val removedId = queue.playOrderIds[queueIndex]
        val newOrderIds = queue.playOrderIds.toMutableList().apply { removeAt(queueIndex) }
        val newCurrentIndex = when {
            orderSize == 1 -> 0
            queueIndex < queue.currentIndex -> queue.currentIndex - 1
            queueIndex == queue.currentIndex -> queue.currentIndex.coerceAtMost(newOrderIds.lastIndex.coerceAtLeast(0))
            else -> queue.currentIndex
        }
        val newSongs = if (newOrderIds.none { it == removedId }) {
            queue.songs.filter { it.id != removedId }
        } else {
            queue.songs
        }
        return queue.copy(songs = newSongs, playOrderIds = newOrderIds, currentIndex = newCurrentIndex)
    }

    /**
     * Moves the play cursor to [index] without reordering or deduping (gate A5). Used when the user
     * taps a queue item to play it — the kernel re-centers its window on this index.
     */
    fun jumpTo(queue: PlayQueue, index: Int): PlayQueue {
        val clamped = index.coerceIn(0, queue.playOrderIds.lastIndex.coerceAtLeast(0))
        return queue.copy(currentIndex = clamped)
    }
}
