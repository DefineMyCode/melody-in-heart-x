package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * 设置页状态。
 *
 * 2026-10-24：主题 / 主题色 / 屏幕方向**移出**设置页（搬到「样式/主题切换」页，
 * 那边才是外观相关的地方），这里只剩播放行为与权限相关项。
 */
@Stable
data class SettingsRouteState(
    val globalUniformRandomEnabled: Boolean,
    val dailyListeningGoalMinutes: Int,
)

@Stable
data class SettingsRouteActions(
    val onBack: () -> Unit,
    val onGlobalUniformRandomEnabledChange: (Boolean) -> Unit,
    val onDailyListeningGoalMinutesChange: (Int) -> Unit,
    val onRequestBluetoothPermission: () -> Unit,
    val onRequestNotificationPermission: () -> Unit,
)

@Composable
fun SettingsRoute(
    state: SettingsRouteState,
    actions: SettingsRouteActions,
) {
    SettingsScreen(
        onBack = actions.onBack,
        globalUniformRandomEnabled = state.globalUniformRandomEnabled,
        onGlobalUniformRandomEnabledChange = actions.onGlobalUniformRandomEnabledChange,
        dailyListeningGoalMinutes = state.dailyListeningGoalMinutes,
        onDailyListeningGoalMinutesChange = actions.onDailyListeningGoalMinutesChange,
        onRequestBluetoothPermission = actions.onRequestBluetoothPermission,
        onRequestNotificationPermission = actions.onRequestNotificationPermission,
    )
}
