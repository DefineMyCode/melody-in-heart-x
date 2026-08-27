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

        // 动态分层（按播放次数值分组，次数最小的组为最高优先层）：
        // 阈值随曲库分布自适应——任何分布下最低层都非空（除非全体次数相同，此时等价纯随机），
        // 避免固定阈值（如"0 次优先"）在曲库全部播放过后分层失效。
        val groups = songs
            .groupBy { playCounts[it.id] ?: 0 }
            .toSortedMap()
            .values
            .toList()

        // 层0 = 最小次数组；层1 = 次小次数组；其余合并为兜底层。组数不足时自然退化。
        val tiered = listOf(
            groups.getOrElse(0) { emptyList() },
            groups.getOrElse(1) { emptyList() },
            groups.drop(2).flatten(),
        )

        // 名额逐层抢占：层内随机，层间按优先级，低层有货就不碰高层。
        val result = mutableListOf<Song>()
        var remaining = neededSize
        for (tier in tiered) {
            if (remaining <= 0) break
            val taken = shuffle(tier).take(minOf(remaining, tier.size))
            result += taken
            remaining -= taken.size
        }
        return result
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
}
