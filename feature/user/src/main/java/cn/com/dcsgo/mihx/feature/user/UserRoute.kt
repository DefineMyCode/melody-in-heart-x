package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo

data class UserRouteState(
    val songs: List<Song>,
    val playlists: List<Playlist>,
    val currentSong: Song?,
    val isPlaying: Boolean,
    val isImporting: Boolean,
    val importProgress: Int,
    val importTotal: Int,
)

data class UserRouteActions(
    val onAddFolderClick: () -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onAddSongsToPlaylist: (List<Song>, Playlist) -> Int,
    val onDeleteSong: (Song) -> Unit,
    val onCreatePlaylist: (String) -> Playlist?,
    val onShowVersionManagement: () -> Unit,
    val onShowQuickSkipSongs: () -> Unit,
    val onShowSettings: () -> Unit,
    val onShowRawPlayStats: () -> Unit,
    val onShowEffectivePlayStats: () -> Unit,
)

@Composable
fun UserRoute(
    state: UserRouteState,
    actions: UserRouteActions,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
) {
    UserScreen(
        songs = state.songs,
        playlists = state.playlists,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        isImporting = state.isImporting,
        importProgress = state.importProgress,
        importTotal = state.importTotal,
        onAddFolderClick = actions.onAddFolderClick,
        onSongClick = { song -> actions.onSongClick(song, state.songs) },
        onAddSongsToPlaylist = { songs, playlist ->
            val added = actions.onAddSongsToPlaylist(songs, playlist)
            showToast(
                if (added > 0) "已将 $added 首歌曲添加到「${playlist.name}」"
                else "这些歌曲已在「${playlist.name}」中",
            )
        },
        onDeleteSong = actions.onDeleteSong,
        onCreatePlaylist = actions.onCreatePlaylist,
        onShowVersionManagement = actions.onShowVersionManagement,
        onShowQuickSkipSongs = actions.onShowQuickSkipSongs,
        onShowSettings = actions.onShowSettings,
        onShowPlayStats = actions.onShowRawPlayStats,
        onShowEffectivePlayStats = actions.onShowEffectivePlayStats,
        loadSongInfo = loadSongInfo,
    )
}
