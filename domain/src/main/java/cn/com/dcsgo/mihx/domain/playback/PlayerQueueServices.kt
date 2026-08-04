package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.model.SongDeletionActions
import cn.com.dcsgo.mihx.domain.playlist.PlaylistActions
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions

data class PlayerQueueServices(
    val importer: FolderImporter,
    val playlistActions: PlaylistActions,
    val songGroupCoordinator: SongGroupCoordinator,
    val songDeletionActions: SongDeletionActions,
    val quickSkipActions: QuickSkipActions,
    val playOrderBuilder: QueueManager.PlayOrderBuilder,
    val queueActionPlanner: PlaybackQueueActionPlanner,
)

fun interface PlayerQueueServicesFactory {
    fun create(): PlayerQueueServices
}
