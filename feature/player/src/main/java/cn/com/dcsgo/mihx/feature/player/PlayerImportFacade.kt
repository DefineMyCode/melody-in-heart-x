package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.importing.ImportResult

class PlayerImportFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val importer: FolderImporter,
    private val launch: (suspend () -> Unit) -> Unit = {},
) {
    fun importFolderAsync(treeUri: Uri, onResult: (Int) -> Unit) {
        importFolderAsyncWith(onResult) {
            importFolder(treeUri)
        }
    }

    internal fun importFolderAsyncWith(
        onResult: (Int) -> Unit,
        importAction: suspend () -> Int,
    ) {
        launch {
            onResult(importAction())
        }
    }

    suspend fun importFolder(treeUri: Uri): Int {
        return importFolderWith { onProgress ->
            importer.importFolder(treeUri, onProgress)
        }
    }

    internal suspend fun importFolderWith(
        importAction: suspend (onProgress: (processed: Int, total: Int) -> Unit) -> ImportResult,
    ): Int {
        updateState {
            it.copy(
                isImporting = true,
                importProgress = 0,
                importTotal = 0,
            )
        }

        val result = importAction { processed, total ->
            updateState {
                it.copy(
                    importProgress = processed,
                    importTotal = total,
                )
            }
        }

        updateState {
            it.copy(
                isImporting = false,
                importProgress = 0,
                importTotal = 0,
                songs = result.songs,
                playlists = result.playlists,
            )
        }
        return result.addedCount
    }
}
