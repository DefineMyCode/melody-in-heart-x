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
    val dailyListeningGoalMinutes: Flow<Int>

    fun currentGlobalUniformRandomEnabled(): Boolean
    fun currentBluetoothPlaybackMonitoringEnabled(): Boolean
    fun currentPlaybackNotificationEnabled(): Boolean
    fun currentDailyListeningGoalMinutes(): Int
    fun setGlobalUniformRandomEnabledBlocking(enabled: Boolean)
    fun setBluetoothPlaybackMonitoringEnabledBlocking(enabled: Boolean)
    fun setPlaybackNotificationEnabledBlocking(enabled: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setThemeVariant(variant: ThemeVariant)
    suspend fun setGlobalUniformRandomEnabled(enabled: Boolean)
    suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean)
    suspend fun setPlaybackNotificationEnabled(enabled: Boolean)
    suspend fun setLyricFontScale(scale: Float)
    suspend fun setDailyListeningGoalMinutes(minutes: Int)
    fun setDailyListeningGoalMinutesBlocking(minutes: Int)

    /** 定时关闭结束时间戳（epoch 毫秒，0 = 未设置） */
    fun currentSleepTimerEndAtMs(): Long
    /** 定时关闭是否「播完最后一曲」 */
    fun currentSleepTimerPlayLastSong(): Boolean
    fun setSleepTimerEndAtMsBlocking(endAtMs: Long)
    fun setSleepTimerPlayLastSongBlocking(enabled: Boolean)

    /** 情绪批扫手动暂停（详情页可暂停/继续） */
    val emotionScanPaused: Flow<Boolean>
    fun currentEmotionScanPaused(): Boolean
    suspend fun setEmotionScanPaused(paused: Boolean)

    /**
     * 情境化随心播放增强总开关（默认关闭）。
     * 开启后随心播放/无限随机在命中时段内只从时段配置的情绪词条歌曲中随机。
     */
    val moodTimeSlotEnabled: Flow<Boolean>
    fun currentMoodTimeSlotEnabled(): Boolean
    suspend fun setMoodTimeSlotEnabled(enabled: Boolean)
}
