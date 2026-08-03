package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant

data class SettingsRouteState(
    val themeMode: ThemeMode,
    val themeVariant: ThemeVariant,
    val globalUniformRandomEnabled: Boolean,
)

data class SettingsRouteActions(
    val onBack: () -> Unit,
    val onThemeModeChange: (ThemeMode) -> Unit,
    val onThemeVariantChange: (ThemeVariant) -> Unit,
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
        themeVariant = state.themeVariant,
        onThemeVariantChange = actions.onThemeVariantChange,
        globalUniformRandomEnabled = state.globalUniformRandomEnabled,
        onGlobalUniformRandomEnabledChange = actions.onGlobalUniformRandomEnabledChange,
        onRequestBluetoothPermission = actions.onRequestBluetoothPermission,
        onRequestNotificationPermission = actions.onRequestNotificationPermission,
    )
}
