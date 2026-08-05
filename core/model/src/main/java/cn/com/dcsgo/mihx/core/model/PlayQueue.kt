package cn.com.dcsgo.mihx.core.model

import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.R

/**
 * 播放模式枚举
 *
 * 定义播放队列的顺序策略。所有模式均支持首尾循环。
 */
enum class PlayMode(val label: String, val icon: Int) {

    /** 顺序播放：从索引 0 → 末尾 → 0 循环 */
    SEQUENTIAL("顺序播放", R.drawable.forward_media_24),

    /** 倒序播放：从末尾 → 索引 0 → 末尾循环 */
    REVERSE("倒序播放", R.drawable.replay_24),

    /** 随机播放：排除当前歌曲，随机选下一首 */
    SHUFFLE("随机播放", R.drawable.shuffle_24);

    /** 循环切换到下一个模式 */
    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = SEQUENTIAL
    }
}

/**
 * 播放队列
 *
 * 独立于歌单/歌曲列表，管理"接下来要播什么"。
 * 特性：
 * - 原始顺序保持不变，用于 UI 展示
 * - 播放顺序由 [playMode] 决定
 * - 当前播放位置通过 [currentIndex] 追踪
 * - 支持添加/移除单首歌，以及整体替换
 */
@Stable
data class PlayQueue(
    /** 队列中的歌曲列表（UI 展示顺序，始终不变） */
    val songs: List<Song> = emptyList(),

    /** 当前播放歌曲在原始队列中的索引，-1 表示未设置 */
    val currentIndex: Int = -1,

    /** 当前播放模式 */
    val playMode: PlayMode = PlayMode.DEFAULT,

    /** 真实播放顺序，元素为 [Song.id]。为空时按 [playMode] 从 [songs] 派生。 */
    val playOrderIds: List<Int> = emptyList(),
) {
    /** 当前正在播放的歌曲，null 表示队列为空或未设置索引 */
    val currentSong: Song?
        get() = songs.getOrNull(currentIndex)

    /** 队列是否为空 */
    val isEmpty: Boolean
        get() = songs.isEmpty()

    /** 队列中的歌曲数量 */
    val size: Int
        get() = songs.size

    // ─────────────────────────────────────────────────────────────
    // 队列操作（返回新的 PlayQueue 实例，保持不可变）
    // ─────────────────────────────────────────────────────────────

    /**
     * 将整个歌曲列表设为队列，并从指定索引开始播放。
     *
     * @param newSongs      新的歌曲列表
     * @param startIndex    开始播放的索引（默认从头开始）
     * @param mode          播放模式（默认保持当前模式）
     */
    fun setQueue(newSongs: List<Song>, startIndex: Int = 0, mode: PlayMode = playMode): PlayQueue {
        if (newSongs.isEmpty()) return copy(songs = emptyList(), currentIndex = -1, playOrderIds = emptyList())
        val idx = startIndex.coerceIn(0, newSongs.size - 1)
        return copy(
            songs = newSongs,
            currentIndex = idx,
            playMode = mode,
            playOrderIds = buildPlayOrderIds(newSongs, idx, mode),
        )
    }

    /**
     * 向队列尾部追加歌曲。
     *
     * @param song 要追加的歌曲
     * @return 如果歌曲已在队列中则不变，否则追加
     */
    fun addSong(song: Song): PlayQueue {
        if (songs.any { it.id == song.id }) return this
        val newSongs = songs + song
        // 如果原队列为空（currentIndex == -1），自动将新歌曲设为当前项
        val newIndex = if (currentIndex < 0) newSongs.size - 1 else currentIndex
        val newOrder = currentPlayOrderIds(songs) + song.id
        return copy(songs = newSongs, currentIndex = newIndex, playOrderIds = newOrder)
    }

    /**
     * 向队列尾部追加多首歌曲，允许同一首歌在队列中出现多次。
     */
    fun addSongs(newSongs: List<Song>): PlayQueue {
        if (newSongs.isEmpty()) return this
        val merged = songs + newSongs
        // 如果原队列为空，自动将第一首新歌曲设为当前项
        val newIndex = if (currentIndex < 0) 0 else currentIndex
        val newOrder = currentPlayOrderIds(songs) + newSongs.map { it.id }
        return copy(songs = merged, currentIndex = newIndex, playOrderIds = newOrder)
    }

    /**
     * 将指定歌曲插入到当前播放歌曲的紧下一位，确保下一首一定是该歌曲。
     *
     * - 若队列为空，则作为第一首歌插入并设为当前播放。
     * - 若歌曲已存在于队列，先将其从原位置移除，再插入到当前播放歌曲的下一位，
     *   保证重复执行"添加到下一首"时总能立即生效。
     *
     * @param song 要插入的歌曲
     */
    fun addSongAsNext(song: Song): PlayQueue {
        if (currentIndex < 0) {
            // 队列为空，插入作为第一首并设为当前播放
            val base = songs.filter { it.id != song.id }
            return copy(
                songs = base + song,
                currentIndex = base.size,
                playOrderIds = base.map { it.id } + song.id,
            )
        }

        // 先从队列中移除旧位置（如果存在）
        val existingIndex = songs.indexOfFirst { it.id == song.id }
        val (songsAfterRemove, adjustedCurrentIndex) = if (existingIndex >= 0) {
            val mutable = songs.toMutableList().apply { removeAt(existingIndex) }
            // 移除后 currentIndex 需要修正（移除点在 currentIndex 之前时）
            val newCurrent = if (existingIndex < currentIndex) currentIndex - 1 else currentIndex
            Pair(mutable, newCurrent)
        } else {
            Pair(songs.toMutableList(), currentIndex)
        }

        // 插入到 adjustedCurrentIndex 的下一位
        val insertIndex = (adjustedCurrentIndex + 1).coerceAtMost(songsAfterRemove.size)
        songsAfterRemove.add(insertIndex, song)

        val currentSongId = songs.getOrNull(currentIndex)?.id
        val baseOrder = currentPlayOrderIds(songsAfterRemove)
            .filter { it != song.id }
            .toMutableList()
        val orderInsertIndex = currentSongId
            ?.let { baseOrder.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?: baseOrder.size
        baseOrder.add(orderInsertIndex.coerceIn(0, baseOrder.size), song.id)

        return copy(
            songs = songsAfterRemove,
            currentIndex = adjustedCurrentIndex,
            playOrderIds = baseOrder,
        )
    }

    /**
     * 将多首歌曲插入到当前播放歌曲后面。
     *
     * 待插入列表按歌曲 ID 去重；队列中已存在的待插入歌曲会被移动到当前位置后面，
     * 当前播放项本身保留为锚点。
     */
    fun addSongsAsNext(newSongs: List<Song>): Pair<PlayQueue, Int>? {
        val uniqueSongs = newSongs.distinctBy { it.id }
        if (uniqueSongs.isEmpty()) return null

        if (currentIndex !in songs.indices) {
            return setQueue(uniqueSongs, startIndex = 0, mode = PlayMode.SEQUENTIAL) to uniqueSongs.size
        }

        val currentSong = songs[currentIndex]
        val insertSongs = uniqueSongs.filter { it.id != currentSong.id }
        if (insertSongs.isEmpty()) return null

        val idsToMove = uniqueSongs.map { it.id }.toSet()
        val updatedSongs = mutableListOf<Song>()
        var updatedCurrentIndex = -1
        songs.forEachIndexed { index, song ->
            val keepSong = index == currentIndex || song.id !in idsToMove
            if (keepSong) {
                if (index == currentIndex) updatedCurrentIndex = updatedSongs.size
                updatedSongs += song
            }
        }

        val insertIndex = (updatedCurrentIndex + 1).coerceIn(0, updatedSongs.size)
        updatedSongs.addAll(insertIndex, insertSongs)

        return copy(
            songs = updatedSongs,
            currentIndex = updatedCurrentIndex,
            playMode = PlayMode.SEQUENTIAL,
            playOrderIds = updatedSongs.map { it.id },
        ) to insertSongs.size
    }

    /**
     * 从队列中移除指定歌曲。
     *
     * 如果移除的是当前播放的歌曲，自动调整 currentIndex。
     */
    fun removeSong(songId: Int): PlayQueue {
        val index = songs.indexOfFirst { it.id == songId }
        if (index < 0) return this

        return removeSongAt(index)
    }

    fun removeSongAt(index: Int): PlayQueue {
        if (index !in songs.indices) return this
        val newSongs = songs.toMutableList().apply { removeAt(index) }
        val newCurrentIndex = when {
            newSongs.isEmpty() -> -1
            index < currentIndex -> currentIndex - 1         // 移除在当前播放之前
            index == currentIndex -> {
                // 移除的是当前歌曲：保持同一位置，不超过边界
                currentIndex.coerceAtMost(newSongs.size - 1)
            }
            else -> currentIndex                             // 移除在当前播放之后，不影响
        }
        val newOrder = currentPlayOrderIds(newSongs)
        return copy(songs = newSongs, currentIndex = newCurrentIndex, playOrderIds = newOrder)
    }

    /** 切换播放模式 */
    fun setPlayMode(mode: PlayMode): PlayQueue {
        return copy(
            playMode = mode,
            playOrderIds = buildPlayOrderIds(songs, currentIndex, mode),
        )
    }

    /** 清空队列 */
    fun clear(): PlayQueue = copy(songs = emptyList(), currentIndex = -1, playOrderIds = emptyList())

    fun withCurrentIndex(index: Int): PlayQueue {
        if (songs.isEmpty()) return copy(currentIndex = -1, playOrderIds = emptyList())
        val idx = index.coerceIn(0, songs.lastIndex)
        // 随机模式且已有完整播放顺序时，稳定地把当前歌曲移到队首，不再重新洗牌。
        // 避免随机模式下每次同步/换歌都生成新乱序，导致控制器列表被反复替换（死循环）。
        return if (playMode == PlayMode.SHUFFLE && orderCoversSongs(playOrderIds, songs)) {
            val targetId = songs[idx].id
            if (playOrderIds.firstOrNull() == targetId) {
                copy(currentIndex = idx)
            } else {
                val pivot = playOrderIds.indexOf(targetId).takeIf { it >= 0 }
                    ?: return copy(currentIndex = idx, playOrderIds = buildPlayOrderIds(songs, idx, playMode))
                copy(currentIndex = idx, playOrderIds = playOrderIds.drop(pivot) + playOrderIds.take(pivot))
            }
        } else {
            copy(currentIndex = idx, playOrderIds = buildPlayOrderIds(songs, idx, playMode))
        }
    }

    fun withSongs(newSongs: List<Song>): PlayQueue {
        if (newSongs.isEmpty()) return clear()
        val currentSongId = currentSong?.id
        val newIndex = currentSongId
            ?.let { id -> newSongs.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: currentIndex.coerceIn(0, newSongs.lastIndex)
        return copy(
            songs = newSongs,
            currentIndex = newIndex,
            playOrderIds = currentPlayOrderIds(newSongs).filter { id -> newSongs.any { it.id == id } },
        )
    }

    fun currentPlayOrderIds(sourceSongs: List<Song> = songs): List<Int> {
        val remainingCounts = sourceSongs
            .groupingBy { it.id }
            .eachCount()
            .toMutableMap()
        val normalized = mutableListOf<Int>()
        playOrderIds.forEach { id ->
            val remaining = remainingCounts[id] ?: 0
            if (remaining > 0) {
                normalized += id
                remainingCounts[id] = remaining - 1
            }
        }
        val missing = mutableListOf<Int>()
        sourceSongs.forEach { song ->
            val remaining = remainingCounts[song.id] ?: 0
            if (remaining > 0) {
                missing += song.id
                remainingCounts[song.id] = remaining - 1
            }
        }
        return (normalized + missing).ifEmpty {
            buildPlayOrderIds(sourceSongs, currentIndex.coerceAtLeast(0), playMode)
        }
    }

    fun currentPlayOrderIndices(): List<Int> {
        if (songs.isEmpty()) return emptyList()
        return when (playMode) {
            PlayMode.SEQUENTIAL -> songs.indices.toList()
            PlayMode.REVERSE -> songs.indices.reversed().toList()
            PlayMode.SHUFFLE -> {
                val used = BooleanArray(songs.size)
                val indices = mutableListOf<Int>()
                currentPlayOrderIds().forEachIndexed { orderIndex, songId ->
                    val preferredIndex = if (
                        orderIndex == 0 &&
                        currentIndex in songs.indices &&
                        songs[currentIndex].id == songId &&
                        !used[currentIndex]
                    ) {
                        currentIndex
                    } else {
                        songs.indices.firstOrNull { !used[it] && songs[it].id == songId }
                    }
                    if (preferredIndex != null) {
                        used[preferredIndex] = true
                        indices += preferredIndex
                    }
                }
                songs.indices.filterTo(indices) { !used[it] }
                indices
            }
        }
    }

    companion object {
        /** playOrderIds 是否为 songs 的完整排列（数量与 id 计数一致） */
        private fun orderCoversSongs(playOrderIds: List<Int>, songs: List<Song>): Boolean {
            if (playOrderIds.size != songs.size) return false
            return songs.groupingBy { it.id }.eachCount() == playOrderIds.groupingBy { it }.eachCount()
        }

        fun buildPlayOrderIds(songs: List<Song>, startIndex: Int, mode: PlayMode): List<Int> {
            if (songs.isEmpty()) return emptyList()
            val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
            val ordered = when (mode) {
                PlayMode.SEQUENTIAL -> songs
                PlayMode.REVERSE -> songs.asReversed()
                PlayMode.SHUFFLE -> {
                    val current = songs[safeIndex]
                    listOf(current) + songs.filterIndexed { index, _ -> index != safeIndex }.shuffled()
                }
            }
            return ordered.map { it.id }
        }
    }
}
