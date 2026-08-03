package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface PlayerSettingsRepository {
    val themeMode: Flow<ThemeMode>
    val globalUniformRandomEnabled: Flow<Boolean>
    val bluetoothPlaybackMonitoringEnabled: Flow<Boolean>
    val playbackNotificationEnabled: Flow<Boolean>

    fun currentGlobalUniformRandomEnabled(): Boolean
    fun currentBluetoothPlaybackMonitoringEnabled(): Boolean
    fun currentPlaybackNotificationEnabled(): Boolean
    fun setGlobalUniformRandomEnabledBlocking(enabled: Boolean)
    fun setBluetoothPlaybackMonitoringEnabledBlocking(enabled: Boolean)
    fun setPlaybackNotificationEnabledBlocking(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setGlobalUniformRandomEnabled(enabled: Boolean)
    suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean)
    suspend fun setPlaybackNotificationEnabled(enabled: Boolean)
}
