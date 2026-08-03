package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.QueueManager
import cn.com.dcsgo.mihx.domain.playback.SongGroupCoordinator
import cn.com.dcsgo.mihx.domain.playback.SongGroupUpdate
import cn.com.dcsgo.mihx.domain.playback.SongVersionManager

class PlayerVersionFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val songGroupCoordinator: SongGroupCoordinator,
    private val savePlaybackState: () -> Unit,
    private val playFromQueue: (PlayQueue, Int) -> Unit,
    private val playNext: () -> Unit,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
    private val playOrderBuilder: QueueManager.PlayOrderBuilder = QueueManager.defaultPlayOrderBuilder,
) {
    fun updateSameNameSongs(song: Song) {
        val sameNameSongs = SongVersionManager.sortedSameNameSongs(song, state().songs, isPlayable)
        updateState { it.copy(sameNameSongs = sameNameSongs) }
    }

    fun switchToVersion(targetSong: Song) {
        if (!isPlayable(targetSong)) return

        when (val plan = SongVersionManager.switchToVersion(state().playQueue, targetSong)) {
            is SongVersionManager.SwitchPlan.PlayExisting -> {
                val queue = QueueManager.withCurrentIndex(plan.queue, plan.index, playOrderBuilder)
                updateState { it.copy(playQueue = queue) }
                savePlaybackState()
                playFromQueue(queue, plan.index)
            }
            is SongVersionManager.SwitchPlan.InsertNext -> {
                updateState { it.copy(playQueue = plan.queue) }
                savePlaybackState()
                playNext()
            }
            null -> Unit
        }
    }

    fun getGroupedSongs(songs: List<Song>): List<List<Song>> {
        return SongVersionManager.groupedSongs(songs, isPlayable)
    }

    fun getSongsWithSameName(song: Song, songs: List<Song>): List<Song> {
        return SongVersionManager.sameNameSongs(song, songs, isPlayable)
    }

    fun detachSongFromGroup(song: Song): Boolean {
        return applySongGroupUpdate(
            songGroupCoordinator.detachFromGroup(song, state().currentSong?.id)
        )
    }

    fun reassignSongToGroup(song: Song, targetSong: Song): Boolean {
        return applySongGroupUpdate(songGroupCoordinator.reassignToGroup(song, targetSong))
    }

    fun resetSongGroupKey(song: Song): Boolean {
        return applySongGroupUpdate(songGroupCoordinator.resetGroupKey(song))
    }

    private fun applySongGroupUpdate(update: SongGroupUpdate): Boolean {
        if (!update.updated) return false
        updateState { current ->
            current.copy(
                songs = update.songs,
                currentSong = update.currentSong ?: current.currentSong,
                sameNameSongs = update.sameNameSongs ?: current.sameNameSongs,
            )
        }
        return true
    }
}
