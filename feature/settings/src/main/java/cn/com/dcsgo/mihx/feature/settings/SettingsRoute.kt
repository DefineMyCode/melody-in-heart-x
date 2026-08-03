package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable

data class SettingsRouteState(
    val darkThemeEnabled: Boolean,
    val globalUniformRandomEnabled: Boolean,
)

data class SettingsRouteActions(
    val onBack: () -> Unit,
    val onDarkThemeEnabledChange: (Boolean) -> Unit,
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
        darkThemeEnabled = state.darkThemeEnabled,
        onDarkThemeEnabledChange = actions.onDarkThemeEnabledChange,
        globalUniformRandomEnabled = state.globalUniformRandomEnabled,
        onGlobalUniformRandomEnabledChange = actions.onGlobalUniformRandomEnabledChange,
        onRequestBluetoothPermission = actions.onRequestBluetoothPermission,
        onRequestNotificationPermission = actions.onRequestNotificationPermission,
    )
}
