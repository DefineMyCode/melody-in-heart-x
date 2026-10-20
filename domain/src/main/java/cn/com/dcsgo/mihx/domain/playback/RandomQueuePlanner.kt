package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

class RandomQueuePlanner(
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val shuffle: (List<Song>) -> List<Song> = { it.shuffled() },
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
    private val uniformRandomPlanner: UniformRandomPlanner = UniformRandomPlanner(shuffle),
) {
    data class RandomQueuePlan(
        val songs: List<Song>,
        val recentSongIds: Set<Int>,
        val resetHistory: Boolean,
    )

    data class InfiniteStartPlan(
        val playedSongIds: Set<Int>,
    )

    data class InfiniteRefillPlan(
        val queue: PlayQueue,
        val addedSongs: List<Song>,
        val playedSongIds: Set<Int>,
        val resetHistory: Boolean,
    )

    data class ExploreRefillPlan(
        val queue: PlayQueue,
        val addedSongs: List<Song>,
    )

    /**
     * 探索播放的队列补充计划（2026-10-20）：从候选池随机取 [count] 首追加到队尾，
     * 排除已在队列中的歌曲；候选池由调用方按喜欢/不喜欢词条预过滤。
     * @return null 表示无可播放歌曲；[addedSongs] 为空表示候选池已全部在队列中。
     */
    fun planExploreRefill(
        allSongs: List<Song>,
        queue: PlayQueue,
        count: Int,
    ): ExploreRefillPlan? {
        val playableSongs = allSongs.playable()
        if (playableSongs.isEmpty()) return null

        val existingQueueIds = queue.songs.map { it.id }.toSet()
        val available = playableSongs.filter { it.id !in existingQueueIds }
        val selected = shuffle(available).take(count)
        if (selected.isEmpty()) {
            return ExploreRefillPlan(queue = queue, addedSongs = emptyList())
        }
        return ExploreRefillPlan(
            queue = queue.withSongs(queue.songs + selected),
            addedSongs = selected,
        )
    }

    fun planRandomQueue(
        songs: List<Song>,
        recentSongIds: Set<Int>,
        uniformRandomEnabled: Boolean = false,
        playCounts: Map<Int, Int> = emptyMap(),
        batchSize: Int = this.batchSize,
    ): RandomQueuePlan? {
        val playableSongs = songs.playable()
        if (playableSongs.isEmpty()) return null

        val neededSize = minOf(batchSize, playableSongs.size)
        val recentIds = recentSongIds.toMutableSet()
        var availableSongs = playableSongs.filter { it.id !in recentIds }
        var resetHistory = false

        if (availableSongs.size < neededSize) {
            recentIds.clear()
            availableSongs = playableSongs
            resetHistory = true
        }

        val selectedSongs = if (uniformRandomEnabled) {
            uniformRandomPlanner.selectSongs(
                songs = availableSongs,
                neededSize = neededSize,
                uniformRandomEnabled = true,
                playCounts = playCounts,
            )
        } else {
            shuffle(shuffle(availableSongs).take(neededSize))
        }
        recentIds.addAll(selectedSongs.map { it.id })
        recentIds.trimToLimit(playableSongs.size)

        return RandomQueuePlan(
            songs = selectedSongs,
            recentSongIds = recentIds,
            resetHistory = resetHistory,
        )
    }

    fun planInfiniteStart(
        allSongs: List<Song>,
        queue: PlayQueue,
    ): InfiniteStartPlan? {
        val playableSongs = allSongs.playable()
        if (playableSongs.isEmpty()) return null

        val playableIds = playableSongs.map { it.id }.toSet()
        val queuedPlayableIds = queue.songs
            .map { it.id }
            .filter { it in playableIds }
            .toSet()

        return InfiniteStartPlan(
            playedSongIds = queuedPlayableIds,
        )
    }

    fun planInfiniteRefill(
        allSongs: List<Song>,
        queue: PlayQueue,
        playedSongIds: Set<Int>,
        uniformRandomEnabled: Boolean = false,
        playCounts: Map<Int, Int> = emptyMap(),
    ): InfiniteRefillPlan? {
        val playableSongs = allSongs.playable()
        if (playableSongs.isEmpty()) return null

        val neededSize = minOf(batchSize, playableSongs.size)
        val updatedPlayedIds = playedSongIds.toMutableSet()
        var availableSongs = playableSongs.filter { it.id !in updatedPlayedIds }
        var resetHistory = false

        if (availableSongs.size < neededSize) {
            updatedPlayedIds.clear()
            availableSongs = playableSongs
            resetHistory = true
        }

        val existingQueueIds = queue.songs.map { it.id }.toSet()
        val selectedSongs = uniformRandomPlanner.selectSongs(
            songs = availableSongs.filter { it.id !in existingQueueIds },
            neededSize = neededSize,
            uniformRandomEnabled = uniformRandomEnabled,
            playCounts = playCounts,
        )
        if (selectedSongs.isEmpty()) {
            return InfiniteRefillPlan(
                queue = queue,
                addedSongs = emptyList(),
                playedSongIds = updatedPlayedIds,
                resetHistory = resetHistory,
            )
        }

        updatedPlayedIds.addAll(selectedSongs.map { it.id })
        return InfiniteRefillPlan(
            queue = queue.withSongs(queue.songs + selectedSongs),
            addedSongs = selectedSongs,
            playedSongIds = updatedPlayedIds,
            resetHistory = resetHistory,
        )
    }

    private fun List<Song>.playable(): List<Song> = filter(isPlayable)

    private fun MutableSet<Int>.trimToLimit(limit: Int) {
        if (size <= limit) return
        val toRemove = toList().take(size - limit)
        removeAll(toRemove.toSet())
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 20
    }
}
