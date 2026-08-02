package cn.com.dcsgo.mihx.feature.settings

import cn.com.dcsgo.mihx.domain.repository.ThemeMode

/**
 * UI state for the 设置 screen (plan P5-C4). Defaults mirror the Preferences DataStore defaults
 * (uniform-random ON, bluetooth/notification ON, infinite-play OFF, theme SYSTEM, dynamic color ON)
 * so the UI renders correctly before the first DataStore emission arrives.
 */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val uniformRandomEnabled: Boolean = true,
    val infinitePlayEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = true,
    val notificationEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
)
