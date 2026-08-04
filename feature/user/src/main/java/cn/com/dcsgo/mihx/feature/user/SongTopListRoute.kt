package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Song

data class SongTopListRouteState(
    val weeklyTop: List<Pair<Int, Int>>,
    val monthlyTop: List<Pair<Int, Int>>,
    val songs: List<Song>,
    val currentSong: Song?,
)

data class SongTopListRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song) -> Unit,
)

@Composable
fun SongTopListRoute(
    state: SongTopListRouteState,
    actions: SongTopListRouteActions,
) {
    SongTopListScreen(
        weeklyTop = state.weeklyTop,
        monthlyTop = state.monthlyTop,
        songs = state.songs,
        currentSong = state.currentSong,
        onBack = actions.onBack,
        onSongClick = actions.onSongClick,
    )
}
