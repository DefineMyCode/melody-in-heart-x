package cn.com.dcsgo.mihx.data.player

import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PlaybackStateStoreTest {

    @Test
    fun saveAndRestoreKeepsQueuePositionModeAndOrder() = runStoreTest { store, _ ->
        val queue = PlayQueue()
            .setQueue(songs(1, 2, 3), startIndex = 1, mode = PlayMode.SHUFFLE)
            .copy(playOrderIds = listOf(2, 3, 1))

        store.save(queue, positionMs = 42_000L)

        val restored = store.restore(songs(1, 2, 3))

        assertEquals(listOf(1, 2, 3), restored?.queue?.songs?.map { it.id })
        assertEquals(1, restored?.queue?.currentIndex)
        assertEquals(PlayMode.SHUFFLE, restored?.queue?.playMode)
        assertEquals(listOf(2, 3, 1), restored?.queue?.currentPlayOrderIds())
        assertEquals(42_000L, restored?.positionMs)
    }

    @Test
    fun restoreFiltersSongsThatAreNoLongerAvailable() = runStoreTest { store, _ ->
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 2, mode = PlayMode.SEQUENTIAL)

        store.save(queue, positionMs = 100L)

        val restored = store.restore(songs(1, 3))

        assertEquals(listOf(1, 3), restored?.queue?.songs?.map { it.id })
        assertEquals(1, restored?.queue?.currentIndex)
        assertEquals(100L, restored?.positionMs)
    }

    @Test
    fun saveAndRestoreKeepsDuplicateQueueItems() = runStoreTest { store, _ ->
        val queue = PlayQueue()
            .setQueue(songs(1, 2, 2, 3), startIndex = 2, mode = PlayMode.SEQUENTIAL)

        store.save(queue, positionMs = 100L)

        val restored = store.restore(songs(1, 2, 3))

        assertEquals(listOf(1, 2, 2, 3), restored?.queue?.songs?.map { it.id })
        assertEquals(2, restored?.queue?.currentIndex)
        assertEquals(listOf(1, 2, 2, 3), restored?.queue?.currentPlayOrderIds())
    }

    @Test
    fun saveAndRestoreKeepsInfinitePlayState() = runStoreTest { store, _ ->
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        store.save(
            queue = queue,
            positionMs = 100L,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2, 99),
        )

        val restored = store.restore(songs(1, 2, 3))

        assertEquals(true, restored?.isInfinitePlay)
        assertEquals(setOf(1, 2), restored?.infinitePlayedSongIds)
    }

    @Test
    fun saveAndRestoreKeepsInfinitePlayWhenQueueIsEmpty() = runStoreTest { store, _ ->
        store.save(
            queue = PlayQueue(),
            positionMs = 100L,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1),
        )

        val restored = store.restore(songs(1, 2))

        assertEquals(emptyList<Int>(), restored?.queue?.songs?.map { it.id })
        assertEquals(true, restored?.isInfinitePlay)
        assertEquals(setOf(1), restored?.infinitePlayedSongIds)
    }

    @Test
    fun restoreUsesCurrentSongSnapshotToCorrectQueueIndexAndPosition() = runStoreTest { store, _ ->
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 1)

        store.save(queue, positionMs = 10L)
        store.saveCurrentPlaybackSnapshot(songId = 1, positionMs = 60_000L)

        val restored = store.restore(songs(1, 2))

        assertEquals(0, restored?.queue?.currentIndex)
        assertEquals(1, restored?.queue?.currentSong?.id)
        assertEquals(60_000L, restored?.positionMs)
    }

    @Test
    fun restoreCurrentSongSnapshotCreatesQueueWhenSongWasNotInSavedQueue() = runStoreTest { store, _ ->
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        store.save(queue, positionMs = 10L)
        store.saveCurrentPlaybackSnapshot(songId = 3, positionMs = 60_000L)

        val restored = store.restore(songs(1, 2, 3))

        assertEquals(listOf(3), restored?.queue?.songs?.map { it.id })
        assertEquals(0, restored?.queue?.currentIndex)
        assertEquals(60_000L, restored?.positionMs)
    }

    @Test
    fun emptySessionSaveKeepsExistingSnapshot() = runStoreTest { store, _ ->
        store.save(PlayQueue().setQueue(songs(1), startIndex = 0), positionMs = 100L)

        // UI 重建窗口的瞬时全空状态（queue 空 + 无 currentSongId）不得清掉已有快照，
        // 否则重启 restore 读到"无快照"、播放队列恒为空（2026-09-03 回归）。
        store.save(PlayQueue(), positionMs = 200L)

        val restored = store.restore(songs(1))
        assertEquals(listOf(1), restored?.queue?.songs?.map { it.id })
        assertEquals(100L, restored?.positionMs)
    }

    @Test
    fun emptySessionSaveWithoutExistingSnapshotWritesNothing() = runStoreTest { store, _ ->
        store.save(PlayQueue(), positionMs = 200L)

        assertNull(store.restore(songs(1)))
    }

    @Test
    fun restoreFallsBackToLegacyPrefsAndNextSaveClearsLegacyState() = runBlocking {
        val legacyPrefs = FakeSharedPreferences(
            mapOf(
                "play_queue_json" to """{"songIds":[1,2],"currentIndex":1,"playMode":"DEFAULT","playOrderIds":[1,2]}""",
                "play_position_ms" to 500L,
                "is_infinite_play" to true,
                "infinite_played_ids" to "[1]",
                "current_song_id" to 2,
            )
        )
        val handle = newDataStoreHandle()
        val store = PlaybackStateStore(handle.store, legacyPrefs)

        try {
            val restored = store.restore(songs(1, 2))

            assertEquals(2, restored?.queue?.currentSong?.id)
            assertEquals(500L, restored?.positionMs)
            assertEquals(true, restored?.isInfinitePlay)

            store.save(PlayQueue().setQueue(songs(1), startIndex = 0), positionMs = 100L)

            assertFalse(legacyPrefs.contains("play_queue_json"))
            val savedJson = handle.store.data.first()[stringKey("play_queue_json")]
            val savedQueue = PlaybackStateSnapshotSerializer().decodeQueue(
                json = savedJson.orEmpty(),
                allSongs = songs(1),
                allowEmpty = false,
            )
            assertEquals(listOf(1), savedQueue?.songs?.map { it.id })
            assertEquals(0, savedQueue?.currentIndex)
            assertEquals(PlayMode.SEQUENTIAL, savedQueue?.playMode)
        } finally {
            handle.close()
        }
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }

    private fun runStoreTest(block: suspend (PlaybackStateStore, FakeSharedPreferences) -> Unit) = runBlocking {
        val handle = newDataStoreHandle()
        val legacyPrefs = FakeSharedPreferences()
        val store = PlaybackStateStore(handle.store, legacyPrefs)
        try {
            block(store, legacyPrefs)
        } finally {
            handle.close()
        }
    }

    private fun newDataStoreHandle(): DataStoreHandle {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File.createTempFile("playback-state-", ".preferences_pb").apply { delete() }
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return DataStoreHandle(store, scope, file)
    }

    private class DataStoreHandle(
        val store: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        private val scope: CoroutineScope,
        private val file: File,
    ) {
        fun close() {
            scope.cancel()
            file.delete()
        }
    }

    private fun stringKey(name: String) = androidx.datastore.preferences.core.stringPreferencesKey(name)

    private class FakeSharedPreferences(
        initialValues: Map<String, Any?> = emptyMap(),
    ) : SharedPreferences {
        private val values = initialValues.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? {
            return values[key] as? String ?: defValue
        }

        override fun getLong(key: String?, defValue: Long): Long {
            return values[key] as? Long ?: defValue
        }

        override fun getInt(key: String?, defValue: Int): Int {
            return values[key] as? Int ?: defValue
        }

        override fun getBoolean(key: String?, defValue: Boolean): Boolean {
            return values[key] as? Boolean ?: defValue
        }

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun getAll(): MutableMap<String, *> = values
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return values[key] as? MutableSet<String> ?: defValues
        }

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val updates = mutableMapOf<String, Any?>()
            private var shouldClear = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = null
            }

            override fun clear(): SharedPreferences.Editor = apply {
                shouldClear = true
            }

            override fun apply() {
                commit()
            }

            override fun commit(): Boolean {
                if (shouldClear) values.clear()
                updates.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                return true
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = value
            }

            override fun putStringSet(
                key: String?,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                if (key != null) updates[key] = values
            }
        }
    }
}
