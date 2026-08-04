package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import kotlinx.coroutines.flow.Flow

interface PlayerSettingsRepository {
    val themeMode: Flow<ThemeMode>
    val themeVariant: Flow<ThemeVariant>
    val globalUniformRandomEnabled: Flow<Boolean>
    val bluetoothPlaybackMonitoringEnabled: Flow<Boolean>
    val playbackNotificationEnabled: Flow<Boolean>
    val lyricFontScale: Flow<Float>

    fun currentGlobalUniformRandomEnabled(): Boolean
    fun currentBluetoothPlaybackMonitoringEnabled(): Boolean
    fun currentPlaybackNotificationEnabled(): Boolean
    fun setGlobalUniformRandomEnabledBlocking(enabled: Boolean)
    fun setBluetoothPlaybackMonitoringEnabledBlocking(enabled: Boolean)
    fun setPlaybackNotificationEnabledBlocking(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeVariant(variant: ThemeVariant)
    suspend fun setGlobalUniformRandomEnabled(enabled: Boolean)
    suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean)
    suspend fun setPlaybackNotificationEnabled(enabled: Boolean)
    suspend fun setLyricFontScale(scale: Float)

    /** 定时关闭结束时间戳（epoch 毫秒，0 = 未设置） */
    fun currentSleepTimerEndAtMs(): Long
    /** 定时关闭是否「播完最后一曲」 */
    fun currentSleepTimerPlayLastSong(): Boolean
    fun setSleepTimerEndAtMsBlocking(endAtMs: Long)
    fun setSleepTimerPlayLastSongBlocking(enabled: Boolean)
}
