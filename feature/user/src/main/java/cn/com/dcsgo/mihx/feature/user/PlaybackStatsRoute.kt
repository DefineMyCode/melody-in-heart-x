package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot

data class PlaybackStatsRouteState(
    val snapshot: PlaybackStatsSnapshot,
    val songs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
    /** 每日听歌时长目标（分钟），0 表示无目标，默认 120 */
    val dailyListeningGoalMinutes: Int = 120,
)

data class PlaybackStatsRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song) -> Unit,
    val onOpenPlayCounts: () -> Unit,
    val onOpenEffectivePlayCounts: () -> Unit,
    val onOpenWeeklyTop: () -> Unit,
    val onOpenMonthlyTop: () -> Unit,
)

@Composable
fun PlaybackStatsRoute(
    state: PlaybackStatsRouteState,
    actions: PlaybackStatsRouteActions,
) {
    PlaybackStatsScreen(
        snapshot = state.snapshot,
        songs = state.songs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        dailyListeningGoalMinutes = state.dailyListeningGoalMinutes,
        onBack = actions.onBack,
        onSongClick = actions.onSongClick,
        onOpenPlayCounts = actions.onOpenPlayCounts,
        onOpenEffectivePlayCounts = actions.onOpenEffectivePlayCounts,
        onOpenWeeklyTop = actions.onOpenWeeklyTop,
        onOpenMonthlyTop = actions.onOpenMonthlyTop,
    )
}
