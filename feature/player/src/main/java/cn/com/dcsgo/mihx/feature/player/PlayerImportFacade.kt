package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.importing.ImportResult
import kotlinx.coroutines.CancellationException

class PlayerImportFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val importer: FolderImporter,
    private val launch: (suspend () -> Unit) -> Unit = {},
    // 沿用 PlayerLifecycleFacade 的日志注入模式：JVM 单测不触碰 android.util.Log
    private val logError: (String, Throwable) -> Unit = { _, _ -> },
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
            try {
                onResult(importAction())
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // C-1（评审 2026-09-03）：导入异常若不处理，isImporting 永不复位，
                // UI 永久卡在"导入中"且无任何日志——这里兜底复位 + 上报错误。
                logError("importFolder failed: ${t.message}", t)
                updateState {
                    it.copy(
                        isImporting = false,
                        importProgress = 0,
                        importTotal = 0,
                        errorMessage = "导入失败：${t.message ?: "未知错误"}，请重试",
                    )
                }
                onResult(0)
            }
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

        try {
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
        } catch (ce: CancellationException) {
            // 协程取消（页面销毁等）：不再吞异常，但保证 isImporting 复位，避免 UI 卡死。
            updateState {
                it.copy(isImporting = false, importProgress = 0, importTotal = 0)
            }
            throw ce
        } catch (t: Throwable) {
            // 双保险：异步路径的 catch 只覆盖 importFolderAsyncWith 的 launch 块，
            // suspend 直接调用方（如测试或未来新入口）异常同样要复位导入态。
            updateState {
                it.copy(isImporting = false, importProgress = 0, importTotal = 0)
            }
            throw t
        }
    }
}
