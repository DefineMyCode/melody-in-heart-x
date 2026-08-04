package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playlist.PlaylistActions

class PlayerPlaylistFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val playlistManager: PlaylistActions,
) {
    fun getSongsByPlaylist(playlist: Playlist): List<Song> {
        return playlistManager.getSongsByPlaylist(playlist, state().songs)
    }

    fun createPlaylist(name: String): Playlist? {
        val playlist = playlistManager.createPlaylist(name)
        refresh()
        return playlist
    }

    fun deletePlaylist(playlistId: Int) {
        playlistManager.deletePlaylist(playlistId)
        refresh()
    }

    fun renamePlaylist(playlistId: Int, newName: String): Boolean {
        val result = playlistManager.renamePlaylist(playlistId, newName)
        if (result) refresh()
        return result
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
        val result = playlistManager.addSongToPlaylist(playlistId, songId)
        if (result) refresh()
        return result
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        playlistManager.removeSongFromPlaylist(playlistId, songId)
        refresh()
    }

    fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>) {
        playlistManager.reorderPlaylist(playlistId, orderedSongIds)
        refresh()
    }

    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistManager.isSongInPlaylist(playlistId, songId)
    }

    fun refresh() {
        val snapshot = playlistManager.snapshot()
        updateState {
            it.copy(
                playlists = snapshot.playlists,
                songs = snapshot.songs,
            )
        }
    }
}
