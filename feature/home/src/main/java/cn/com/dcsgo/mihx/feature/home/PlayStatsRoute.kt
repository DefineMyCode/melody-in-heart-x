package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Song

data class PlayStatsRouteState(
    val title: String,
    val songs: List<Song>,
    val playCounts: Map<Int, Int>,
    val currentSong: Song?,
)

data class PlayStatsRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song) -> Unit,
)

@Composable
fun PlayStatsRoute(
    state: PlayStatsRouteState,
    actions: PlayStatsRouteActions,
) {
    PlayStatsScreen(
        title = state.title,
        songs = state.songs,
        playCounts = state.playCounts,
        currentSong = state.currentSong,
        onBack = actions.onBack,
        onSongClick = actions.onSongClick,
    )
}
