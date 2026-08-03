package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song

class UniformRandomPlanner(
    private val shuffle: (List<Song>) -> List<Song> = { it.shuffled() },
) {
    fun selectSongs(
        songs: List<Song>,
        neededSize: Int,
        uniformRandomEnabled: Boolean,
        playCounts: Map<Int, Int>,
    ): List<Song> {
        if (songs.isEmpty() || neededSize <= 0) return emptyList()
        if (!uniformRandomEnabled) return shuffle(songs).take(neededSize)

        val targetPoolSize = minOf(
            songs.size,
            maxOf(MIN_UNIFORM_POOL_SIZE, neededSize * POOL_SIZE_MULTIPLIER),
        )
        val candidatePool = songs
            .groupBy { playCounts[it.id] ?: 0 }
            .toSortedMap()
            .values
            .fold(emptyList<Song>()) { pool, group ->
                if (pool.size >= targetPoolSize) {
                    pool
                } else {
                    pool + group
                }
            }

        return shuffle(candidatePool).take(neededSize)
    }

    fun orderSongs(
        songs: List<Song>,
        uniformRandomEnabled: Boolean,
        playCounts: Map<Int, Int>,
    ): List<Song> {
        if (!uniformRandomEnabled) return shuffle(songs)
        return songs
            .groupBy { playCounts[it.id] ?: 0 }
            .toSortedMap()
            .values
            .flatMap { group -> shuffle(group) }
    }

    fun buildPlayOrderIds(
        songs: List<Song>,
        startIndex: Int,
        mode: PlayMode,
        uniformRandomEnabled: Boolean,
        playCounts: Map<Int, Int>,
    ): List<Int> {
        if (songs.isEmpty()) return emptyList()
        val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
        val ordered = when (mode) {
            PlayMode.SEQUENTIAL -> songs
            PlayMode.REVERSE -> songs.asReversed()
            PlayMode.SHUFFLE -> {
                val current = songs[safeIndex]
                listOf(current) + orderSongs(
                    songs = songs.filterIndexed { index, _ -> index != safeIndex },
                    uniformRandomEnabled = uniformRandomEnabled,
                    playCounts = playCounts,
                )
            }
        }
        return ordered.map { it.id }
    }

    companion object {
        const val MIN_UNIFORM_POOL_SIZE = 60
        const val POOL_SIZE_MULTIPLIER = 3
    }
}
