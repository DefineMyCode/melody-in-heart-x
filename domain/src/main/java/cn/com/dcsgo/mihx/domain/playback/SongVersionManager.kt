package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

object SongVersionManager {
    sealed class SwitchPlan {
        data class PlayExisting(val queue: PlayQueue, val index: Int) : SwitchPlan()
        data class InsertNext(val queue: PlayQueue, val index: Int) : SwitchPlan()
    }

    fun groupedSongs(songs: List<Song>, isPlayable: (Song) -> Boolean = { it.uri != null }): List<List<Song>> {
        return songs.filter(isPlayable)
            .groupBy { it.groupKey }
            .values
            .toList()
    }

    fun sameNameSongs(
        song: Song,
        songs: List<Song>,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): List<Song> {
        return songs.filter { it.groupKey == song.groupKey && isPlayable(it) }
    }

    fun sortedSameNameSongs(
        song: Song,
        songs: List<Song>,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): List<Song> {
        return sameNameSongs(song, songs, isPlayable).sortedByDescending { it.sampleRate }
    }

    fun switchToVersion(queue: PlayQueue, targetSong: Song): SwitchPlan? {
        if (queue.isEmpty) return null

        val existingIndex = queue.songs.indexOfFirst { it.id == targetSong.id }
        if (existingIndex >= 0) {
            return SwitchPlan.PlayExisting(
                queue = queue.withCurrentIndex(existingIndex),
                index = existingIndex,
            )
        }

        val currentIndex = queue.currentIndex.coerceIn(0, queue.songs.lastIndex)
        val updatedSongs = queue.songs.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(updatedSongs.size)
        updatedSongs.add(insertIndex, targetSong)

        return SwitchPlan.InsertNext(
            queue = queue.withSongs(updatedSongs),
            index = insertIndex,
        )
    }

    fun replaceCurrentInQueue(queue: PlayQueue, targetSong: Song): PlayQueue? {
        if (queue.isEmpty) return null

        val targetIndex = queue.songs.indexOfFirst { it.id == targetSong.id }
        if (targetIndex >= 0) {
            return queue.withCurrentIndex(targetIndex)
        }

        val updatedSongs = queue.songs.toMutableList()
        val currentIndex = queue.currentIndex.coerceIn(0, updatedSongs.lastIndex)
        updatedSongs[currentIndex] = targetSong
        return queue.withSongs(updatedSongs)
    }

    fun detachedGroupKey(song: Song): String = "${song.title}#${song.id}"
}
