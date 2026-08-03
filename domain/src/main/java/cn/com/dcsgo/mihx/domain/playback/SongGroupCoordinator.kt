package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.Song

data class SongGroupUpdate(
    val updated: Boolean,
    val songs: List<Song> = emptyList(),
    val currentSong: Song? = null,
    val sameNameSongs: List<Song>? = null,
)

class SongGroupCoordinator(
    private val updateSongTitleOverride: (songId: Int, titleOverride: String?) -> Boolean,
    private val getSongs: () -> List<Song>,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) {
    fun detachFromGroup(song: Song, currentSongId: Int?): SongGroupUpdate {
        return updateGroupKey(
            song = song,
            titleOverride = SongVersionManager.detachedGroupKey(song),
            currentSongId = currentSongId,
        )
    }

    fun reassignToGroup(song: Song, targetSong: Song): SongGroupUpdate {
        return updateGroupKey(
            song = song,
            titleOverride = targetSong.groupKey,
            currentSongId = null,
        )
    }

    fun resetGroupKey(song: Song): SongGroupUpdate {
        return updateGroupKey(
            song = song,
            titleOverride = null,
            currentSongId = null,
        )
    }

    private fun updateGroupKey(
        song: Song,
        titleOverride: String?,
        currentSongId: Int?,
    ): SongGroupUpdate {
        if (!updateSongTitleOverride(song.id, titleOverride)) {
            return SongGroupUpdate(updated = false)
        }

        val songs = getSongs()
        val currentSong = if (currentSongId == song.id) {
            songs.find { it.id == song.id }
        } else {
            null
        }
        return SongGroupUpdate(
            updated = true,
            songs = songs,
            currentSong = currentSong,
            sameNameSongs = currentSong
                ?.let { SongVersionManager.sortedSameNameSongs(it, songs, isPlayable) },
        )
    }
}
