package cn.com.dcsgo.mihx.domain.repository

import kotlinx.coroutines.flow.Flow

/** App theme selection persisted in [PlayerSettingsRepository]. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Global player toggles (plan P4-3). Backed by a Preferences DataStore; uniform-random defaults
 * to ON, bluetooth / notification default ON, infinite-play defaults OFF.
 *
 * Every toggle exposes both a one-shot suspend getter (for kernel-side decisions) and a [Flow]
 * (for the settings screen and for live-reacting consumers such as the queue facade).
 */
interface PlayerSettingsRepository {
    suspend fun isUniformRandomEnabled(): Boolean
    suspend fun setUniformRandomEnabled(enabled: Boolean)
    fun observeUniformRandomEnabled(): Flow<Boolean>

    suspend fun isBluetoothEnabled(): Boolean
    suspend fun setBluetoothEnabled(enabled: Boolean)
    fun observeBluetoothEnabled(): Flow<Boolean>

    suspend fun isNotificationEnabled(): Boolean
    suspend fun setNotificationEnabled(enabled: Boolean)
    fun observeNotificationEnabled(): Flow<Boolean>

    suspend fun isInfinitePlayEnabled(): Boolean
    suspend fun setInfinitePlayEnabled(enabled: Boolean)
    fun observeInfinitePlayEnabled(): Flow<Boolean>

    suspend fun getThemeMode(): ThemeMode
    suspend fun setThemeMode(mode: ThemeMode)
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun isDynamicColorEnabled(): Boolean
    suspend fun setDynamicColorEnabled(enabled: Boolean)
    fun observeDynamicColorEnabled(): Flow<Boolean>
}
