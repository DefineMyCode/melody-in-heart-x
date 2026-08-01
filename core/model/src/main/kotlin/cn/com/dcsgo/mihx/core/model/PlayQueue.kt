package cn.com.dcsgo.mihx.core.model

/**
 * Business playback queue — source of truth for UI display & queue operations.
 *
 * IMPORTANT: [songs] may contain the same [Song.id] multiple times. Every
 * operation (save / restore / remove / highlight) MUST be done by queue index,
 * never by assuming [Song.id] is unique within the queue. Do NOT use
 * [List.distinct], [associateBy] or [Set] keyed by id here.
 */
data class PlayQueue(
    val songs: List<Song>,
    val currentIndex: Int,
    val playMode: PlayMode,
    val playOrderIds: List<Long>,
) {
    /**
     * Resolve [playOrderIds] back to queue item indices, preserving repeated ids.
     * e.g. ids [1,2,1] with songs containing id 1 at indices 0 and 4 -> [0,?,4].
     */
    fun currentPlayOrderIndices(): List<Int> {
        val remaining = songs.mapIndexed { index, song -> song.id to index }.toMutableList()
        return playOrderIds.mapNotNull { wanted ->
            val hit = remaining.firstOrNull { it.first == wanted }
            if (hit != null) {
                remaining.remove(hit)
                hit.second
            } else {
                null
            }
        }
    }
}
