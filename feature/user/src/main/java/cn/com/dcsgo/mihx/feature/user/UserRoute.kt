package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

@Stable
data class UserRouteState(
    /** 今日累计听歌时长（毫秒），用于「我的」页入口卡预览 */
    val todayDurationMs: Long = 0L,
    /** 本周累计听歌时长（毫秒），用于「我的」页入口卡预览 */
    val weekTotalMs: Long = 0L,
)

data class UserRouteActions(
    val onShowSettings: () -> Unit,
    val onShowPlaybackStats: () -> Unit,
)

@Composable
fun UserRoute(
    state: UserRouteState,
    actions: UserRouteActions,
) {
    UserScreen(
        onShowSettings = actions.onShowSettings,
        todayDurationMs = state.todayDurationMs,
        weekTotalMs = state.weekTotalMs,
        onOpenPlaybackStats = actions.onShowPlaybackStats,
    )
}
