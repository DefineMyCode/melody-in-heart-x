package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

class PlayerSettingsRepository(
    private val settingsStore: DataStore<Preferences>,
    private val legacyPrefs: SharedPreferences,
) : cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository {

    constructor(context: Context) : this(
        settingsStore = context.applicationContext.playerSettingsDataStore,
        legacyPrefs = context.applicationContext.getSharedPreferences(
            PlayerSettingsKeys.LEGACY_PREFS_NAME,
            Context.MODE_PRIVATE,
        ),
    )

    override val darkTheme: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.DARK_THEME]
            ?: legacyPrefs.getBoolean(PlayerSettingsKeys.LEGACY_DARK_THEME, false)
    }

    override val globalUniformRandomEnabled: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.GLOBAL_UNIFORM_RANDOM_ENABLED]
            ?: legacyPrefs.getBoolean(PlayerSettingsKeys.LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED, true)
    }

    override val bluetoothPlaybackMonitoringEnabled: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.BLUETOOTH_PLAYBACK_MONITORING_ENABLED]
            ?: legacyPrefs.getBoolean(PlayerSettingsKeys.LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED, false)
    }

    override val playbackNotificationEnabled: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.PLAYBACK_NOTIFICATION_ENABLED]
            ?: legacyPrefs.getBoolean(PlayerSettingsKeys.LEGACY_PLAYBACK_NOTIFICATION_ENABLED, false)
    }

    override fun currentGlobalUniformRandomEnabled(): Boolean {
        return runBlocking(Dispatchers.IO) {
            globalUniformRandomEnabled.first()
        }
    }

    override fun currentBluetoothPlaybackMonitoringEnabled(): Boolean {
        return runBlocking(Dispatchers.IO) {
            bluetoothPlaybackMonitoringEnabled.first()
        }
    }

    override fun currentPlaybackNotificationEnabled(): Boolean {
        return runBlocking(Dispatchers.IO) {
            playbackNotificationEnabled.first()
        }
    }

    override fun setGlobalUniformRandomEnabledBlocking(enabled: Boolean) {
        runBlocking(Dispatchers.IO) {
            setGlobalUniformRandomEnabled(enabled)
        }
    }

    override fun setBluetoothPlaybackMonitoringEnabledBlocking(enabled: Boolean) {
        runBlocking(Dispatchers.IO) {
            setBluetoothPlaybackMonitoringEnabled(enabled)
        }
    }

    override fun setPlaybackNotificationEnabledBlocking(enabled: Boolean) {
        runBlocking(Dispatchers.IO) {
            setPlaybackNotificationEnabled(enabled)
        }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.DARK_THEME] = enabled
        }
        legacyPrefs.edit()
            .remove(PlayerSettingsKeys.LEGACY_DARK_THEME)
            .apply()
    }

    override suspend fun setGlobalUniformRandomEnabled(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.GLOBAL_UNIFORM_RANDOM_ENABLED] = enabled
        }
        legacyPrefs.edit()
            .remove(PlayerSettingsKeys.LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED)
            .apply()
    }

    override suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.BLUETOOTH_PLAYBACK_MONITORING_ENABLED] = enabled
        }
        legacyPrefs.edit()
            .remove(PlayerSettingsKeys.LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED)
            .apply()
    }

    override suspend fun setPlaybackNotificationEnabled(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.PLAYBACK_NOTIFICATION_ENABLED] = enabled
        }
        legacyPrefs.edit()
            .remove(PlayerSettingsKeys.LEGACY_PLAYBACK_NOTIFICATION_ENABLED)
            .apply()
    }
}
