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
    fun delete(songId: Int): SongDeletionPlan
}
