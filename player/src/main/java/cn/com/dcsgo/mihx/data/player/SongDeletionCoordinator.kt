package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.SongDeletionActions
import cn.com.dcsgo.mihx.domain.model.SongDeletionPlan

class SongDeletionCoordinator(
    private val deleteSong: suspend (songId: Int) -> DeleteSongResult,
) : SongDeletionActions {
    override suspend fun delete(songId: Int): SongDeletionPlan {
        val result = deleteSong(songId)
        return when (result) {
            is DeleteSongResult.Success -> SongDeletionPlan(
                result = result,
                removeFromQueueSongId = result.song.id,
                shouldRefreshLibrary = true,
            )

            is DeleteSongResult.Failure -> SongDeletionPlan(result = result)
        }
    }
}
