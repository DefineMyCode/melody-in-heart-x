package cn.com.dcsgo.mihx.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.feature.player.PlayerQueueFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val facade: PlaylistFacade,
    private val playerQueueFacade: PlayerQueueFacade,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            facade.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun onOpenPlaylist(id: Long) {
        val playlist = _uiState.value.playlists.find { it.id == id } ?: return
        _uiState.update {
            it.copy(
                selectedPlaylistId = id,
                selectedPlaylist = Playlist(id = playlist.id, name = playlist.name),
                isLoading = true,
            )
        }
        viewModelScope.launch {
            val songs = facade.getSongs(id)
            _uiState.update { it.copy(detailSongs = songs, isLoading = false) }
        }
    }

    fun onBack() {
        _uiState.update { it.copy(selectedPlaylistId = null, selectedPlaylist = null, detailSongs = emptyList()) }
    }

    fun onCreateClick() = _uiState.update { it.copy(dialog = PlaylistDialog.Create) }

    /** Opens the rename dialog for the playlist (the row's edit menu fires this). */
    fun onRenameClick(id: Long) {
        val name = _uiState.value.playlists.find { it.id == id }?.name ?: ""
        _uiState.update { it.copy(dialog = PlaylistDialog.Rename(id, name)) }
    }

    /** Deletes a playlist directly (no confirmation — the row edit menu acts as the confirm step). */
    fun deletePlaylist(id: Long) {
        viewModelScope.launch { facade.deletePlaylist(id) }
    }

    fun onDialogDismiss() = _uiState.update { it.copy(dialog = PlaylistDialog.None) }

    fun onCreateConfirm(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotBlank()) {
            viewModelScope.launch { facade.createPlaylist(trimmed) }
        }
        onDialogDismiss()
    }

    fun onRenameConfirm(name: String) {
        val dialog = _uiState.value.dialog
        if (dialog is PlaylistDialog.Rename) {
            val trimmed = name.trim()
            if (trimmed.isNotBlank()) {
                viewModelScope.launch { facade.renamePlaylist(dialog.id, trimmed) }
            }
        }
        onDialogDismiss()
    }

    fun onRemoveSong(songId: Long) {
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        viewModelScope.launch {
            facade.removeSong(playlistId, songId)
            _uiState.update { it.copy(detailSongs = facade.getSongs(playlistId)) }
        }
    }

    /** Plays the playlist from [index]: sets the queue, seeks to the tapped song, and starts playback.
     * The session is connected first so this works even before the player screen has been opened. */
    fun onPlaySong(index: Int) {
        val songs = _uiState.value.detailSongs
        if (index !in songs.indices) return
        playerQueueFacade.connect()
        playerQueueFacade.setQueue(songs, currentIndex = index)
        playerQueueFacade.play()
    }

    /** Moves a song within the current playlist and persists the new manual order. */
    fun onMove(from: Int, to: Int) {
        val playlistId = _uiState.value.selectedPlaylistId ?: return
        if (from == to) return
        val current = _uiState.value.detailSongs.toMutableList()
        if (from !in current.indices || to !in current.indices) return
        val moved = current.removeAt(from)
        current.add(to, moved)
        _uiState.update { it.copy(detailSongs = current) }
        viewModelScope.launch { facade.reorder(playlistId, current.map { it.id }) }
    }
}
