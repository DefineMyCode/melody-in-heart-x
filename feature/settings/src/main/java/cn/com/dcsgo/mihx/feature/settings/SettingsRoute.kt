package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.ThemeMode

data class SettingsRouteState(
    val themeMode: ThemeMode,
    val globalUniformRandomEnabled: Boolean,
)

data class SettingsRouteActions(
    val onBack: () -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onGlobalUniformRandomEnabledChange: (Boolean) -> Unit,
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
        themeMode = state.themeMode,
        onThemeModeChange = actions.onThemeModeChange,
        globalUniformRandomEnabled = state.globalUniformRandomEnabled,
        onGlobalUniformRandomEnabledChange = actions.onGlobalUniformRandomEnabledChange,
        onRequestBluetoothPermission = actions.onRequestBluetoothPermission,
        onRequestNotificationPermission = actions.onRequestNotificationPermission,
    )
}
