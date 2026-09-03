package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.importing.ImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerImportFacadeTest {

    private var state = PlayerUiState()
    private val importer = FakeFolderImporter()
    private val launchedTasks = mutableListOf<suspend () -> Unit>()
    private val facade = PlayerImportFacade(
        updateState = { transform -> state = transform(state) },
        importer = importer,
        launch = { task -> launchedTasks += task },
    )

    @Test
    fun importFolderUpdatesProgressAndFinalLibrarySnapshot() {
        importer.result = ImportResult(
            addedCount = 2,
            songs = songs(1, 2),
            playlists = listOf(playlist(1, "Imported")),
        )

        val count = importWithFake()

        assertEquals(2, count)
        assertFalse(state.isImporting)
        assertEquals(0, state.importProgress)
        assertEquals(0, state.importTotal)
        assertEquals(listOf(1, 2), state.songs.map { it.id })
        assertEquals(listOf("Imported"), state.playlists.map { it.name })
        assertEquals(listOf(1 to 3, 3 to 3), importer.progressEvents)
    }

    @Test
    fun importFolderMarksImportingBeforeImporterRuns() {
        importer.onStart = {
            assertTrue(state.isImporting)
            assertEquals(0, state.importProgress)
            assertEquals(0, state.importTotal)
        }

        importWithFake()
    }

    @Test
    fun importFolderAsyncLaunchesImportAndReportsResult() {
        importer.result = ImportResult(
            addedCount = 4,
            songs = songs(1, 2, 3, 4),
            playlists = emptyList(),
        )
        val results = mutableListOf<Int>()

        facade.importFolderAsyncWith(
            onResult = { results += it },
            importAction = {
                facade.importFolderWith { onProgress ->
                    importer.importFolderWithoutUri(onProgress)
                }
            },
        )
        kotlinx.coroutines.runBlocking {
            launchedTasks.single().invoke()
        }

        assertEquals(listOf(4), results)
        assertEquals(listOf(1, 2, 3, 4), state.songs.map { it.id })
    }

    @Test
    fun `importFolderAsyncWith failure resets importing state and reports zero`() {
        // C-1 回归（评审 2026-09-03）：导入抛异常时 isImporting 必须复位、
        // onResult 必须收到 0，否则 UI 永久卡在"导入中"。
        val results = mutableListOf<Int>()

        facade.importFolderAsyncWith(
            onResult = { results += it },
            importAction = {
                facade.importFolderWith {
                    throw IllegalStateException("disk full")
                }
            },
        )
        kotlinx.coroutines.runBlocking {
            launchedTasks.single().invoke()
        }

        assertEquals(listOf(0), results)
        assertFalse(state.isImporting)
        assertEquals(0, state.importProgress)
        assertEquals(0, state.importTotal)
        assertTrue(state.errorMessage.orEmpty().contains("导入失败"))
    }

    @Test
    fun `importFolderWith failure resets importing state and rethrows`() {
        // C-1 双保险回归：suspend 直接调用路径的异常同样要复位导入态，并原样上抛。
        val thrown = runCatching {
            kotlinx.coroutines.runBlocking {
                facade.importFolderWith {
                    throw IllegalStateException("saf revoked")
                }
            }
        }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException)
        assertFalse(state.isImporting)
        assertEquals(0, state.importProgress)
        assertEquals(0, state.importTotal)
    }

    private class FakeFolderImporter : FolderImporter {
        var result = ImportResult(
            addedCount = 0,
            songs = emptyList(),
            playlists = emptyList(),
        )
        val progressEvents = mutableListOf<Pair<Int, Int>>()
        var onStart: (() -> Unit)? = null

        override suspend fun importFolder(
            treeUri: android.net.Uri,
            onProgress: (processed: Int, total: Int) -> Unit,
        ): ImportResult = importFolderWithoutUri(onProgress)

        suspend fun importFolderWithoutUri(
            onProgress: (processed: Int, total: Int) -> Unit,
        ): ImportResult {
            onStart?.invoke()
            onProgress(1, 3)
            progressEvents += 1 to 3
            onProgress(3, 3)
            progressEvents += 3 to 3
            return result
        }
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }

    private fun playlist(id: Int, name: String): Playlist {
        return Playlist(
            id = id,
            name = name,
            songCount = 0,
        )
    }

    private fun importWithFake(): Int {
        return kotlinx.coroutines.runBlocking {
            facade.importFolderWith { onProgress ->
                importer.importFolderWithoutUri(onProgress)
            }
        }
    }
}
