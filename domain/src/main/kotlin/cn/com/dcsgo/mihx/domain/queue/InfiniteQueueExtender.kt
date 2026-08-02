package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import javax.inject.Inject

/**
 * Picks the next batch of songs to append when infinite play is on and the queue is running dry
 * (plan P5-C3).
 *
 * Selection policy — "cover the library, then loop":
 * 1. Songs from the library that the queue has not covered yet win, shuffled by
 *    [RandomQueuePlanner]. This makes a long listening session walk the whole library instead of
 *    orbiting the same handful of tracks.
 * 2. Once every playable song has been covered, the batch is drawn from the full playable library
 *    again (still shuffled), so playback never ends.
 *
 * Unplayable songs (no URI / marked unplayable on import) are never picked — they would only be
 * filtered out again by [PlaybackWindowPlanner].
 *
 * Pure & side-effect free apart from the injected shuffle.
 */
class InfiniteQueueExtender @Inject constructor(
    private val randomQueuePlanner: RandomQueuePlanner,
) {

    /**
     * Returns up to [batchSize] songs to append to [queue]. An empty result means there is nothing
     * playable to add (empty library), and the caller must not touch the queue.
     */
    fun nextBatch(
        queue: PlayQueue,
        library: List<Song>,
        batchSize: Int = DEFAULT_BATCH_SIZE,
        isPlayable: (Song) -> Boolean = { it.playable && it.uri != null },
    ): List<Song> {
        if (batchSize <= 0) return emptyList()
        val playable = library.filter(isPlayable)
        if (playable.isEmpty()) return emptyList()

        // Plain loops instead of toSet()/associateBy(): architecture gate A5 forbids id-keyed
        // collapsing anywhere near the queue, and the lookup tables below are local scratch state
        // that never becomes part of the queue itself.
        val covered = HashSet<Long>(queue.playOrderIds.size)
        for (id in queue.playOrderIds) covered.add(id)
        val byId = HashMap<Long, Song>(playable.size)
        for (song in playable) byId[song.id] = song

        val uncovered = ArrayList<Long>(playable.size)
        for (song in playable) {
            if (!covered.contains(song.id)) uncovered.add(song.id)
        }
        val pool = if (uncovered.isNotEmpty()) {
            uncovered
        } else {
            val all = ArrayList<Long>(playable.size)
            for (song in playable) all.add(song.id)
            all
        }

        val picked = randomQueuePlanner.plan(pool).take(batchSize)
        val batch = ArrayList<Song>(picked.size)
        for (id in picked) {
            val song = byId[id]
            if (song != null) batch.add(song)
        }
        return batch
    }

    companion object {
        /** How many songs a single top-up appends. */
        const val DEFAULT_BATCH_SIZE = 50

        /**
         * Remaining songs after the current position that trigger a top-up. Kept well below the
         * window look-ahead so the extra items are already loaded by the time playback gets there.
         */
        const val DEFAULT_TRIGGER_REMAINING = 10
    }
}
