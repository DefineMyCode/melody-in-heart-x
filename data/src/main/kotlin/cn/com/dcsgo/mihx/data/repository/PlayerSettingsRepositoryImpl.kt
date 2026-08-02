package cn.com.dcsgo.mihx.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import cn.com.dcsgo.mihx.data.datastore.PlayerSettingsDataStore
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlayerSettingsRepositoryImpl @Inject constructor(
    private val dataStore: PlayerSettingsDataStore,
) : PlayerSettingsRepository {

    private val ds = dataStore.dataStore

    override suspend fun isUniformRandomEnabled(): Boolean =
        read(PlayerSettingsDataStore.UNIFORM_RANDOM, true)

    override suspend fun setUniformRandomEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.UNIFORM_RANDOM] = enabled }
    }

    override fun observeUniformRandomEnabled(): Flow<Boolean> =
        observe(PlayerSettingsDataStore.UNIFORM_RANDOM, true)

    override suspend fun isBluetoothEnabled(): Boolean =
        read(PlayerSettingsDataStore.BLUETOOTH, true)

    override suspend fun setBluetoothEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.BLUETOOTH] = enabled }
    }

    override fun observeBluetoothEnabled(): Flow<Boolean> =
        observe(PlayerSettingsDataStore.BLUETOOTH, true)

    override suspend fun isNotificationEnabled(): Boolean =
        read(PlayerSettingsDataStore.NOTIFICATION, true)

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.NOTIFICATION] = enabled }
    }

    override fun observeNotificationEnabled(): Flow<Boolean> =
        observe(PlayerSettingsDataStore.NOTIFICATION, true)

    override suspend fun isInfinitePlayEnabled(): Boolean =
        read(PlayerSettingsDataStore.INFINITE_PLAY, false)

    override suspend fun setInfinitePlayEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.INFINITE_PLAY] = enabled }
    }

    override fun observeInfinitePlayEnabled(): Flow<Boolean> =
        observe(PlayerSettingsDataStore.INFINITE_PLAY, false)

    override suspend fun getThemeMode(): ThemeMode =
        toThemeMode(ds.data.first()[PlayerSettingsDataStore.THEME_MODE])

    override suspend fun setThemeMode(mode: ThemeMode) {
        ds.edit { it[PlayerSettingsDataStore.THEME_MODE] = mode.name }
    }

    override fun observeThemeMode(): Flow<ThemeMode> =
        ds.data.map { toThemeMode(it[PlayerSettingsDataStore.THEME_MODE]) }

    override suspend fun isDynamicColorEnabled(): Boolean =
        read(PlayerSettingsDataStore.DYNAMIC_COLOR, false)

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.DYNAMIC_COLOR] = enabled }
    }

    override fun observeDynamicColorEnabled(): Flow<Boolean> =
        observe(PlayerSettingsDataStore.DYNAMIC_COLOR, false)

    private suspend fun read(key: Preferences.Key<Boolean>, default: Boolean): Boolean =
        ds.data.first()[key] ?: default

    private fun observe(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        ds.data.map { it[key] ?: default }

    /** Unknown / corrupted persisted values fall back to [ThemeMode.SYSTEM] instead of throwing. */
    private fun toThemeMode(raw: String?): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
}
