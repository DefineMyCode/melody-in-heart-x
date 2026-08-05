package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.Song

@Stable
data class SongTopListRouteState(
    val weeklyTop: List<Pair<Int, Int>>,
    val monthlyTop: List<Pair<Int, Int>>,
    val songs: List<Song>,
    val currentSong: Song?,
    /** 初始选中时间段，"week" / "month"，默认本周 */
    val initialPeriod: String = "week",
)

data class SongTopListRouteActions(
    val onBack: () -> Unit,
    /** 点击歌曲，第二个参数为该歌曲所在时间段（周/月）的整个榜单 */
    val onSongClick: (Song, List<Song>) -> Unit,
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
        initialPeriod = state.initialPeriod,
        onBack = actions.onBack,
        onSongClick = actions.onSongClick,
    )
}
