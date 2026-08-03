package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.SongDeletionActions
import cn.com.dcsgo.mihx.domain.model.SongDeletionPlan

class PlayerSongDeletionFacade(
    private val songDeletionCoordinator: SongDeletionActions,
    private val removeFromPlayQueue: (Int) -> Unit,
    private val refreshPlaylists: () -> Unit,
) {
    fun deleteSong(songId: Int): DeleteSongResult {
        val plan = songDeletionCoordinator.delete(songId)
        applySongDeletionPlan(plan)
        return plan.result
    }

    private fun applySongDeletionPlan(plan: SongDeletionPlan) {
        plan.removeFromQueueSongId?.let(removeFromPlayQueue)
        if (plan.shouldRefreshLibrary) {
            refreshPlaylists()
        }
    }
}
