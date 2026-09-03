package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
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

    override val themeMode: Flow<ThemeMode> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.THEME_MODE]
        if (stored != null) {
            ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
        } else if (preferences[PlayerSettingsKeys.DARK_THEME] != null) {
            if (preferences[PlayerSettingsKeys.DARK_THEME] == true) ThemeMode.DARK else ThemeMode.LIGHT
        } else if (legacyPrefs.contains(PlayerSettingsKeys.LEGACY_DARK_THEME)) {
            if (legacyPrefs.getBoolean(PlayerSettingsKeys.LEGACY_DARK_THEME, false)) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    override val themeVariant: Flow<ThemeVariant> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.THEME_VARIANT]
        ThemeVariant.entries.firstOrNull { it.name == stored } ?: ThemeVariant.MONO
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

    override val lyricFontScale: Flow<Float> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.LYRIC_FONT_SCALE] ?: 1f
    }

    override val dailyListeningGoalMinutes: Flow<Int> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.DAILY_LISTENING_GOAL_MINUTES] ?: 120
    }

    override val emotionScanPaused: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.EMOTION_SCAN_PAUSED] == true
    }

    override fun currentEmotionScanPaused(): Boolean = runBlocking(Dispatchers.IO) {
        emotionScanPaused.first()
    }

    override suspend fun setEmotionScanPaused(paused: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.EMOTION_SCAN_PAUSED] = paused
        }
    }

    override val moodTimeSlotEnabled: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.MOOD_TIME_SLOT_ENABLED] == true
    }

    override fun currentMoodTimeSlotEnabled(): Boolean = runBlocking(Dispatchers.IO) {
        moodTimeSlotEnabled.first()
    }

    override suspend fun setMoodTimeSlotEnabled(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.MOOD_TIME_SLOT_ENABLED] = enabled
        }
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

    override fun currentDailyListeningGoalMinutes(): Int {
        return runBlocking(Dispatchers.IO) {
            dailyListeningGoalMinutes.first()
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

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.THEME_MODE] = mode.name
        }
        legacyPrefs.edit()
            .remove(PlayerSettingsKeys.LEGACY_DARK_THEME)
            .apply()
    }

    override suspend fun setThemeVariant(variant: ThemeVariant) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.THEME_VARIANT] = variant.name
        }
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

    override suspend fun setLyricFontScale(scale: Float) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.LYRIC_FONT_SCALE] = scale
        }
    }

    override suspend fun setDailyListeningGoalMinutes(minutes: Int) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.DAILY_LISTENING_GOAL_MINUTES] = minutes
        }
    }

    override fun setDailyListeningGoalMinutesBlocking(minutes: Int) {
        runBlocking(Dispatchers.IO) {
            setDailyListeningGoalMinutes(minutes)
        }
    }

    override fun currentSleepTimerEndAtMs(): Long {
        return runBlocking(Dispatchers.IO) {
            settingsStore.data.map { preferences ->
                preferences[PlayerSettingsKeys.SLEEP_TIMER_END_AT_MS] ?: 0L
            }.first()
        }
    }

    override fun currentSleepTimerPlayLastSong(): Boolean {
        return runBlocking(Dispatchers.IO) {
            settingsStore.data.map { preferences ->
                preferences[PlayerSettingsKeys.SLEEP_TIMER_PLAY_LAST_SONG] ?: false
            }.first()
        }
    }

    override fun setSleepTimerEndAtMsBlocking(endAtMs: Long) {
        runBlocking(Dispatchers.IO) {
            settingsStore.edit { preferences ->
                preferences[PlayerSettingsKeys.SLEEP_TIMER_END_AT_MS] = endAtMs
            }
        }
    }

    override fun setSleepTimerPlayLastSongBlocking(enabled: Boolean) {
        runBlocking(Dispatchers.IO) {
            settingsStore.edit { preferences ->
                preferences[PlayerSettingsKeys.SLEEP_TIMER_PLAY_LAST_SONG] = enabled
            }
        }
    }
}
