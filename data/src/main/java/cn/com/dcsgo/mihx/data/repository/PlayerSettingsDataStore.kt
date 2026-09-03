package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val PLAYER_SETTINGS_DATASTORE_NAME = "player_settings"

val Context.playerSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PLAYER_SETTINGS_DATASTORE_NAME,
)

object PlayerSettingsKeys {
    const val LEGACY_PREFS_NAME = "music_player_prefs"
    const val LEGACY_DARK_THEME = "dark_theme"
    const val LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED = "global_uniform_random_enabled"
    const val LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED = "bluetooth_playback_monitoring_enabled"
    const val LEGACY_PLAYBACK_NOTIFICATION_ENABLED = "playback_notification_enabled"

    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_VARIANT = stringPreferencesKey("theme_variant")
    val DARK_THEME = booleanPreferencesKey(LEGACY_DARK_THEME)
    val GLOBAL_UNIFORM_RANDOM_ENABLED = booleanPreferencesKey(LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED)
    val BLUETOOTH_PLAYBACK_MONITORING_ENABLED = booleanPreferencesKey(LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED)
    val PLAYBACK_NOTIFICATION_ENABLED = booleanPreferencesKey(LEGACY_PLAYBACK_NOTIFICATION_ENABLED)
    val LYRIC_FONT_SCALE = floatPreferencesKey("lyric_font_scale")

    // 定时关闭：结束时间戳（epoch 毫秒，0 = 未设置）与是否播完最后一曲
    val SLEEP_TIMER_END_AT_MS = longPreferencesKey("sleep_timer_end_at_ms")
    val SLEEP_TIMER_PLAY_LAST_SONG = booleanPreferencesKey("sleep_timer_play_last_song")

    // 每日听歌时长目标（分钟，0 = 未设置）
    val DAILY_LISTENING_GOAL_MINUTES = intPreferencesKey("daily_listening_goal_minutes")

    // 情绪批扫手动暂停（详情页可暂停/继续）
    val EMOTION_SCAN_PAUSED = booleanPreferencesKey("emotion_scan_paused")

    // 情境化随心播放增强总开关（默认关闭）
    val MOOD_TIME_SLOT_ENABLED = booleanPreferencesKey("mood_time_slot_enabled")
}
