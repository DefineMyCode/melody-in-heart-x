package cn.com.dcsgo.mihx.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences DataStore holding the single serialized playback snapshot as a JSON string
 * (plan P4-4 / P4-5). A single row keeps save/restore trivial and avoids a dedicated Room table.
 */
@Singleton
class PlaybackStateDataStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile("playback_state")
    }

    companion object {
        val SNAPSHOT = stringPreferencesKey("snapshot_json")
    }
}
