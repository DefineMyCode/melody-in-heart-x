package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val DARK_THEME = booleanPreferencesKey(LEGACY_DARK_THEME)
    val GLOBAL_UNIFORM_RANDOM_ENABLED = booleanPreferencesKey(LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED)
    val BLUETOOTH_PLAYBACK_MONITORING_ENABLED = booleanPreferencesKey(LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED)
    val PLAYBACK_NOTIFICATION_ENABLED = booleanPreferencesKey(LEGACY_PLAYBACK_NOTIFICATION_ENABLED)
}
