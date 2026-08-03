package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable

data class SettingsRouteState(
    val darkThemeEnabled: Boolean,
    val globalUniformRandomEnabled: Boolean,
    val bluetoothPlaybackMonitoringEnabled: Boolean,
    val playbackNotificationEnabled: Boolean,
)

data class SettingsRouteActions(
    val onBack: () -> Unit,
    val onDarkThemeEnabledChange: (Boolean) -> Unit,
    val onGlobalUniformRandomEnabledChange: (Boolean) -> Unit,
    val onBluetoothPlaybackMonitoringEnabledChange: (Boolean) -> Unit,
    val onPlaybackNotificationEnabledChange: (Boolean) -> Unit,
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
        bluetoothPlaybackMonitoringEnabled = state.bluetoothPlaybackMonitoringEnabled,
        onBluetoothPlaybackMonitoringEnabledChange = actions.onBluetoothPlaybackMonitoringEnabledChange,
        playbackNotificationEnabled = state.playbackNotificationEnabled,
        onPlaybackNotificationEnabledChange = actions.onPlaybackNotificationEnabledChange,
    )
}
