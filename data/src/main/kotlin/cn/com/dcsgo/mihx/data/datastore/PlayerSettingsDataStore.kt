package cn.com.dcsgo.mihx.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore backing [cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository]
 * (plan P4-3). Exposes the raw [DataStore] plus key constants so the repository owns the
 * read/write logic. Key file lives under `dataDir/datastore/`, excluded from cloud backup (P4-8).
 */
@Singleton
class PlayerSettingsDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("player_settings")
    }

    companion object {
        val UNIFORM_RANDOM = booleanPreferencesKey("uniform_random_enabled")
        val BLUETOOTH = booleanPreferencesKey("bluetooth_enabled")
        val NOTIFICATION = booleanPreferencesKey("notification_enabled")
        val INFINITE_PLAY = booleanPreferencesKey("infinite_play_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color_enabled")
    }
}
