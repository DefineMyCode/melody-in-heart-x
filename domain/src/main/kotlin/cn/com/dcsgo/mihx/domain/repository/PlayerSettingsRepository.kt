package cn.com.dcsgo.mihx.domain.repository

/** App theme selection persisted in [PlayerSettingsRepository]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Global player toggles (plan P4-3). Backed by a Preferences DataStore; uniform-random defaults
 * to ON, bluetooth / notification default ON, infinite-play defaults OFF.
 */
interface PlayerSettingsRepository {
    suspend fun isUniformRandomEnabled(): Boolean
    suspend fun setUniformRandomEnabled(enabled: Boolean)

    suspend fun isBluetoothEnabled(): Boolean
    suspend fun setBluetoothEnabled(enabled: Boolean)

    suspend fun isNotificationEnabled(): Boolean
    suspend fun setNotificationEnabled(enabled: Boolean)

    suspend fun isInfinitePlayEnabled(): Boolean
    suspend fun setInfinitePlayEnabled(enabled: Boolean)

    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
}
