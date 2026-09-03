package cn.com.dcsgo.mihx.domain.model

import cn.com.dcsgo.mihx.core.model.Song

sealed class DeleteSongResult {
    data class Success(val song: Song, val message: String) : DeleteSongResult()
    data class Failure(val reason: String) : DeleteSongResult()
}

data class SongDeletionPlan(
    val result: DeleteSongResult,
    val removeFromQueueSongId: Int? = null,
    val shouldRefreshLibrary: Boolean = false,
)

interface SongDeletionActions {
    /** M-3（评审 2026-09-03）：底层为 SAF 跨进程删除，必须挂起执行，避免阻塞主线程 */
    suspend fun delete(songId: Int): SongDeletionPlan
}
