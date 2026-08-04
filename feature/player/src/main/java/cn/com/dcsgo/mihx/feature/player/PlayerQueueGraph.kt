package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServicesFactory

internal class PlayerQueueGraph(
    playerQueueServicesFactory: PlayerQueueServicesFactory,
) {
    private val services = playerQueueServicesFactory.create()

    val importCoordinator = services.importer
    val playlistManager = services.playlistActions
    val songGroupCoordinator = services.songGroupCoordinator
    val songDeletionCoordinator = services.songDeletionActions
    val quickSkipCoordinator = services.quickSkipActions
    val playOrderBuilder = services.playOrderBuilder
    val queueActionPlanner = services.queueActionPlanner
}
