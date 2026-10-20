package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.core.model.ScreenOrientationMode
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.domain.model.SongSortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 播放器设置仓库（DataStore 唯一数据源 + legacy SharedPreferences 只读迁移兜底）。
 *
 * M-5（评审 2026-10-15）：移除全部 current*() 同步快照与 setXxxBlocking ——
 * 旧实现 17 处 runBlocking(IO) 桥，启动路径（PlayerRuntime.start 的 StartupSettings、
 * 定时关闭恢复）与 UI 点击线程都被迫阻塞。现在只有 Flow 观察 + suspend 写入，
 * 读取方在挂起上下文 `first()` 或直接 collect；写入方经协程作用域调度。
 *
 * m-12（评审 2026-10-15）：legacy SharedPreferences 兜底值在 IO 线程读取后并入 DataStore 流，
 * 不在 DataStore 的 map（读线程）里同步读 SharedPreferences 磁盘。
 */
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

    /** 在 IO 线程读一次 legacy 值：key 存在返回其布尔值，不存在返回 null */
    private suspend fun readLegacyBoolean(key: String): Boolean? = withContext(Dispatchers.IO) {
        if (legacyPrefs.contains(key)) legacyPrefs.getBoolean(key, false) else null
    }

    /** DataStore 优先 + legacy SharedPreferences 兜底的布尔设置流（m-12） */
    private fun legacyBackedFlow(key: Preferences.Key<Boolean>, legacyKey: String, legacyDefault: Boolean): Flow<Boolean> = flow {
        val legacy = readLegacyBoolean(legacyKey) ?: legacyDefault
        emitAll(
            settingsStore.data.map { preferences ->
                preferences[key] ?: legacy
            },
        )
    }

    override val themeMode: Flow<ThemeMode> = flow {
        val legacyDarkTheme = readLegacyBoolean(PlayerSettingsKeys.LEGACY_DARK_THEME)
        emitAll(
            settingsStore.data.map { preferences ->
                val stored = preferences[PlayerSettingsKeys.THEME_MODE]
                when {
                    stored != null -> ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
                    preferences[PlayerSettingsKeys.DARK_THEME] != null ->
                        if (preferences[PlayerSettingsKeys.DARK_THEME] == true) ThemeMode.DARK else ThemeMode.LIGHT
                    legacyDarkTheme != null ->
                        if (legacyDarkTheme) ThemeMode.DARK else ThemeMode.LIGHT
                    else -> ThemeMode.SYSTEM
                }
            },
        )
    }

    override val themeVariant: Flow<ThemeVariant> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.THEME_VARIANT]
        ThemeVariant.entries.firstOrNull { it.name == stored } ?: ThemeVariant.MONO
    }

    override val screenOrientationMode: Flow<ScreenOrientationMode> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.SCREEN_ORIENTATION_MODE]
        ScreenOrientationMode.entries.firstOrNull { it.name == stored } ?: ScreenOrientationMode.SENSOR_AUTO
    }

    override val globalUniformRandomEnabled: Flow<Boolean> = legacyBackedFlow(
        key = PlayerSettingsKeys.GLOBAL_UNIFORM_RANDOM_ENABLED,
        legacyKey = PlayerSettingsKeys.LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED,
        legacyDefault = true,
    )

    override val bluetoothPlaybackMonitoringEnabled: Flow<Boolean> = legacyBackedFlow(
        key = PlayerSettingsKeys.BLUETOOTH_PLAYBACK_MONITORING_ENABLED,
        legacyKey = PlayerSettingsKeys.LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED,
        legacyDefault = false,
    )

    override val playbackNotificationEnabled: Flow<Boolean> = legacyBackedFlow(
        key = PlayerSettingsKeys.PLAYBACK_NOTIFICATION_ENABLED,
        legacyKey = PlayerSettingsKeys.LEGACY_PLAYBACK_NOTIFICATION_ENABLED,
        legacyDefault = false,
    )

    override val lyricFontScale: Flow<Float> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.LYRIC_FONT_SCALE] ?: 1f
    }

    override val dailyListeningGoalMinutes: Flow<Int> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.DAILY_LISTENING_GOAL_MINUTES] ?: 120
    }

    override val emotionScanPaused: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.EMOTION_SCAN_PAUSED] == true
    }

    /**
     * 随心播放增强运行模式：优先读新 key；老用户（新 key 未写入）回退到
     * mood_time_slot_enabled 开关（true → 时段模式，false → 关闭），保持既有行为不变。
     */
    override val randomEnhanceMode: Flow<RandomEnhanceMode> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.RANDOM_ENHANCE_MODE]
        when {
            stored != null -> RandomEnhanceMode.entries.firstOrNull { it.name == stored } ?: RandomEnhanceMode.OFF
            preferences[PlayerSettingsKeys.MOOD_TIME_SLOT_ENABLED] == true -> RandomEnhanceMode.TIME_SLOT
            else -> RandomEnhanceMode.OFF
        }
    }

    override val moodTimeSlotEnabled: Flow<Boolean> = randomEnhanceMode.map { it == RandomEnhanceMode.TIME_SLOT }

    // ── 定时关闭 ──

    override val sleepTimerEndAtMs: Flow<Long> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.SLEEP_TIMER_END_AT_MS] ?: 0L
    }

    override val sleepTimerPlayLastSong: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.SLEEP_TIMER_PLAY_LAST_SONG] ?: false
    }

    override suspend fun setSleepTimerEndAtMs(endAtMs: Long) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.SLEEP_TIMER_END_AT_MS] = endAtMs
        }
    }

    override suspend fun setSleepTimerPlayLastSong(enabled: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.SLEEP_TIMER_PLAY_LAST_SONG] = enabled
        }
    }

    // ── 写入 ──

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

    override suspend fun setScreenOrientationMode(mode: ScreenOrientationMode) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.SCREEN_ORIENTATION_MODE] = mode.name
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

    override suspend fun setEmotionScanPaused(paused: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.EMOTION_SCAN_PAUSED] = paused
        }
    }

    override suspend fun setMoodTimeSlotEnabled(enabled: Boolean) {
        setRandomEnhanceMode(if (enabled) RandomEnhanceMode.TIME_SLOT else RandomEnhanceMode.OFF)
    }

    override suspend fun setRandomEnhanceMode(mode: RandomEnhanceMode) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.RANDOM_ENHANCE_MODE] = mode.name
            // 同步写回旧开关：时段模式 → true，其余 → false，保证两条读取路径一致
            preferences[PlayerSettingsKeys.MOOD_TIME_SLOT_ENABLED] = mode == RandomEnhanceMode.TIME_SLOT
        }
    }

    // ── 本地音乐排序 ──

    override val songSortMode: Flow<SongSortMode> = settingsStore.data.map { preferences ->
        val stored = preferences[PlayerSettingsKeys.SONG_SORT_MODE]
        SongSortMode.entries.firstOrNull { it.name == stored } ?: SongSortMode.IMPORT_ORDER
    }

    override suspend fun setSongSortMode(mode: SongSortMode) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.SONG_SORT_MODE] = mode.name
        }
    }

    override val songSortAscending: Flow<Boolean> = settingsStore.data.map { preferences ->
        preferences[PlayerSettingsKeys.SONG_SORT_ASCENDING] ?: true
    }

    override suspend fun setSongSortAscending(ascending: Boolean) {
        settingsStore.edit { preferences ->
            preferences[PlayerSettingsKeys.SONG_SORT_ASCENDING] = ascending
        }
    }
}
