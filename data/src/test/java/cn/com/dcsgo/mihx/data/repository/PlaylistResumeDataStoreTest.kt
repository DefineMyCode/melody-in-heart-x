package cn.com.dcsgo.mihx.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaylistResumeDataStoreTest {

    @Test
    fun recordThenObserveReturnsResume() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            assertNull(repository.observeResume(1).first())

            repository.record(1, 42)

            val resume = repository.observeResume(1).first()
            assertNotNull(resume)
            assertEquals(42, resume?.songId)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun recordSamePlaylistAgainOverwritesSongId() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.record(1, 42)
            repository.record(1, 99)

            val resume = repository.observeResume(1).first()
            assertEquals(99, resume?.songId)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun clearRemovesResumeForThatPlaylistOnly() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.record(1, 42)
            repository.record(2, 7)

            repository.clear(1)

            assertNull(repository.observeResume(1).first())
            assertEquals(7, repository.observeResume(2).first()?.songId)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun corruptedJsonReturnsNull() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            store.edit { preferences ->
                preferences[stringPreferencesKey("resume_1")] = "not-a-json"
            }

            assertNull(repository.observeResume(1).first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun sourcePlaylistDefaultsToNullAndCanBeSetAndCleared() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            assertNull(repository.currentSourcePlaylistId())

            repository.setSourcePlaylist(5)
            assertEquals(5, repository.currentSourcePlaylistId())

            repository.setSourcePlaylist(null)
            assertNull(repository.currentSourcePlaylistId())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun recordCurrentSourceRecordsActualSongAndClearsSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.setSourcePlaylist(3)

            val recorded = repository.recordCurrentSource(88)

            assertTrue(recorded)
            assertEquals(88, repository.observeResume(3).first()?.songId)
            assertNull(repository.currentSourcePlaylistId())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun recordCurrentSourceWithoutSourceReturnsFalse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            val recorded = repository.recordCurrentSource(88)

            assertFalse(recorded)
            assertNull(repository.observeResume(3).first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun switchSourceSettlesOldPlaylistAndWritesNewSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.setSourcePlaylist(1)

            repository.switchSourcePlaylist(newSource = 2, currentSongId = 42)

            assertEquals(42, repository.observeResume(1).first()?.songId)
            assertEquals(2, repository.currentSourcePlaylistId())
            assertNull(repository.observeResume(2).first())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun switchSourceToSamePlaylistDoesNotSettle() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.setSourcePlaylist(1)

            repository.switchSourcePlaylist(newSource = 1, currentSongId = 42)

            assertNull(repository.observeResume(1).first())
            assertEquals(1, repository.currentSourcePlaylistId())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun switchSourceToNonPlaylistSettlesOldPlaylistAndClearsSource() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempDataStoreFile()
        val store = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = PlaylistResumeDataStore(store)

        try {
            repository.setSourcePlaylist(3)

            repository.switchSourcePlaylist(newSource = null, currentSongId = 7)

            assertEquals(7, repository.observeResume(3).first()?.songId)
            assertNull(repository.currentSourcePlaylistId())
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun tempDataStoreFile(): File {
        return File.createTempFile("playlist-resume-", ".preferences_pb").apply {
            delete()
        }
    }
}
