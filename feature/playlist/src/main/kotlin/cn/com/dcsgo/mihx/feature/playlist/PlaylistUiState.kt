package cn.com.dcsgo.mihx.feature.playlist

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.PlaylistWithCover
import cn.com.dcsgo.mihx.core.model.Song

/** UI state for the 歌单 screen: a playlist list, or an opened playlist's detail. */
data class PlaylistUiState(
    val playlists: List<PlaylistWithCover> = emptyList(),
    val selectedPlaylistId: Long? = null,
    val selectedPlaylist: Playlist? = null,
    val detailSongs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val dialog: PlaylistDialog = PlaylistDialog.None,
)

/** Pending dialog surface in the playlist list. The row edit menu offers two options; renaming
 * pops a centered name dialog, deleting fires immediately. */
sealed interface PlaylistDialog {
    data object None : PlaylistDialog
    data object Create : PlaylistDialog
    data class Rename(val id: Long, val currentName: String) : PlaylistDialog
}