package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.Song

@Stable
data class QuickSkipSongsRouteState(
    val songs: List<Song>,
    val currentSong: Song?,
)

data class QuickSkipSongsRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song) -> Unit,
    val onDeleteSong: (Song) -> Unit,
    val onSyncToPlaylist: () -> Unit,
)

@Composable
fun QuickSkipSongsRoute(
    state: QuickSkipSongsRouteState,
    actions: QuickSkipSongsRouteActions,
) {
    QuickSkipSongsScreen(
        songs = state.songs,
        currentSong = state.currentSong,
        onBack = actions.onBack,
        onSongClick = actions.onSongClick,
        onDeleteSong = actions.onDeleteSong,
        onSyncToPlaylist = actions.onSyncToPlaylist,
    )
}
