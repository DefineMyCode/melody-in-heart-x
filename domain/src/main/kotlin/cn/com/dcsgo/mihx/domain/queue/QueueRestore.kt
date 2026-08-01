package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot

/**
 * Pure restore mapping (plan P4-6): rebuilds a [PlayQueue] from the persisted
 * [PlaybackStateSnapshot] against the *current* library (scanned from MediaStore).
 *
 * Rules (gate A5): queue order and repeats are preserved 1:1 — `songIds` is the authoritative
 * play-order id sequence, so we never de-dupe. Songs missing from the current library (deleted
 * file) are dropped and `currentIndex` is clamped to the surviving range. When every id is
 * missing we fall back to the whole library so the user is never left with an empty queue.
 */
object QueueRestore {
    fun restore(library: List<Song>, snapshot: PlaybackStateSnapshot): PlayQueue {
        val byId = library.associateBy { it.id }
        val order = snapshot.songIds.filter { byId.containsKey(it) }
        if (order.isEmpty()) {
            return PlayQueue(
                songs = library,
                currentIndex = 0,
                playMode = snapshot.playMode,
                playOrderIds = library.map { it.id },
            )
        }
        val songs = order.map { byId.getValue(it) }
        val currentIndex = snapshot.currentIndex.coerceIn(0, order.lastIndex)
        return PlayQueue(
            songs = songs,
            currentIndex = currentIndex,
            playMode = snapshot.playMode,
            playOrderIds = order,
        )
    }
}
