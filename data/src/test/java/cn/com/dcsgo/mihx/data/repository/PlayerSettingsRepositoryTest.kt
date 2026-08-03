package cn.com.dcsgo.mihx.data.repository

import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cn.com.dcsgo.mihx.core.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerSettingsRepositoryTest {

    @Test
    fun themeModeFallsBackToLegacyDarkThemeThenWritesDataStoreAndClearsLegacyKey() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences(
            mapOf(PlayerSettingsKeys.LEGACY_DARK_THEME to true),
        )
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertEquals(ThemeMode.DARK, repository.themeMode.first())

            repository.setThemeMode(ThemeMode.LIGHT)

            assertEquals(ThemeMode.LIGHT, repository.themeMode.first())
            assertEquals("LIGHT", store.data.first()[PlayerSettingsKeys.THEME_MODE])
            assertFalse(legacyPrefs.contains(PlayerSettingsKeys.LEGACY_DARK_THEME))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun themeModeDefaultsToSystemWhenNoStoredPreferenceExists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences()
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun lyricFontScaleDefaultsToOneAndPersistsToDataStore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences()
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertEquals(1f, repository.lyricFontScale.first())

            repository.setLyricFontScale(1.4f)

            assertEquals(1.4f, repository.lyricFontScale.first())
            assertEquals(1.4f, store.data.first()[PlayerSettingsKeys.LYRIC_FONT_SCALE])
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun globalUniformRandomFallsBackToLegacyThenWritesDataStoreAndClearsLegacyKey() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences(
            mapOf(PlayerSettingsKeys.LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED to true),
        )
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertTrue(repository.currentGlobalUniformRandomEnabled())

            repository.setGlobalUniformRandomEnabled(false)

            assertFalse(repository.currentGlobalUniformRandomEnabled())
            assertEquals(false, store.data.first()[PlayerSettingsKeys.GLOBAL_UNIFORM_RANDOM_ENABLED])
            assertFalse(legacyPrefs.contains(PlayerSettingsKeys.LEGACY_GLOBAL_UNIFORM_RANDOM_ENABLED))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun globalUniformRandomDefaultsToEnabledWhenNoStoredPreferenceExists() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences()
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertTrue(repository.currentGlobalUniformRandomEnabled())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun bluetoothPlaybackMonitoringFallsBackToLegacyThenWritesDataStoreAndClearsLegacyKey() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences(
            mapOf(PlayerSettingsKeys.LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED to true),
        )
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertTrue(repository.currentBluetoothPlaybackMonitoringEnabled())

            repository.setBluetoothPlaybackMonitoringEnabled(false)

            assertFalse(repository.currentBluetoothPlaybackMonitoringEnabled())
            assertEquals(false, store.data.first()[PlayerSettingsKeys.BLUETOOTH_PLAYBACK_MONITORING_ENABLED])
            assertFalse(legacyPrefs.contains(PlayerSettingsKeys.LEGACY_BLUETOOTH_PLAYBACK_MONITORING_ENABLED))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun playbackNotificationFallsBackToLegacyThenWritesDataStoreAndClearsLegacyKey() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val legacyPrefs = FakeSharedPreferences(
            mapOf(PlayerSettingsKeys.LEGACY_PLAYBACK_NOTIFICATION_ENABLED to true),
        )
        val repository = PlayerSettingsRepository(store, legacyPrefs)

        try {
            assertTrue(repository.currentPlaybackNotificationEnabled())

            repository.setPlaybackNotificationEnabled(false)

            assertFalse(repository.currentPlaybackNotificationEnabled())
            assertEquals(false, store.data.first()[PlayerSettingsKeys.PLAYBACK_NOTIFICATION_ENABLED])
            assertFalse(legacyPrefs.contains(PlayerSettingsKeys.LEGACY_PLAYBACK_NOTIFICATION_ENABLED))
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun tempDataStoreFile(): File {
        return File.createTempFile("player-settings-", ".preferences_pb").apply {
            delete()
        }
    }

    private class FakeSharedPreferences(
        initialValues: Map<String, Any?> = emptyMap(),
    ) : SharedPreferences {
        private val values = initialValues.toMutableMap()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = applyUpdate(key, value)
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = applyUpdate(key, values)
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = applyUpdate(key, value)
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = applyUpdate(key, value)
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = applyUpdate(key, value)
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = applyUpdate(key, value)
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                key?.let {
                    removals += it
                    updates.remove(it)
                }
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clear = true
                updates.clear()
                removals.clear()
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clear) values.clear()
                removals.forEach(values::remove)
                values.putAll(updates)
            }

            private fun applyUpdate(key: String?, value: Any?): SharedPreferences.Editor = apply {
                key?.let {
                    updates[it] = value
                    removals.remove(it)
                }
            }
        }
    }
}
