package cn.com.dcsgo.mihx.data.repository

import androidx.datastore.preferences.core.edit
import cn.com.dcsgo.mihx.data.datastore.PlayerSettingsDataStore
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.ThemeMode
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PlayerSettingsRepositoryImpl @Inject constructor(
    private val dataStore: PlayerSettingsDataStore,
) : PlayerSettingsRepository {

    private val ds = dataStore.dataStore

    override suspend fun isUniformRandomEnabled(): Boolean =
        ds.data.first()[PlayerSettingsDataStore.UNIFORM_RANDOM] ?: true

    override suspend fun setUniformRandomEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.UNIFORM_RANDOM] = enabled }
    }

    override suspend fun isBluetoothEnabled(): Boolean =
        ds.data.first()[PlayerSettingsDataStore.BLUETOOTH] ?: true

    override suspend fun setBluetoothEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.BLUETOOTH] = enabled }
    }

    override suspend fun isNotificationEnabled(): Boolean =
        ds.data.first()[PlayerSettingsDataStore.NOTIFICATION] ?: true

    override suspend fun setNotificationEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.NOTIFICATION] = enabled }
    }

    override suspend fun isInfinitePlayEnabled(): Boolean =
        ds.data.first()[PlayerSettingsDataStore.INFINITE_PLAY] ?: false

    override suspend fun setInfinitePlayEnabled(enabled: Boolean) {
        ds.edit { it[PlayerSettingsDataStore.INFINITE_PLAY] = enabled }
    }

    override suspend fun getThemeMode(): ThemeMode =
        ThemeMode.valueOf(ds.data.first()[PlayerSettingsDataStore.THEME_MODE] ?: ThemeMode.SYSTEM.name)

    override suspend fun setThemeMode(mode: ThemeMode) {
        ds.edit { it[PlayerSettingsDataStore.THEME_MODE] = mode.name }
    }
}
