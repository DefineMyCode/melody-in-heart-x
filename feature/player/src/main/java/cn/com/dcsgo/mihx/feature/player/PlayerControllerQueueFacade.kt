package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort

data class ControllerQueueInfo(
    val mediaItemCount: Int,
    val currentMediaItemIndex: Int,
)

class PlayerControllerQueueFacade(
    private val state: () -> PlayerUiState,
    private val controllerQueueInfo: () -> ControllerQueueInfo?,
    private val clearPlaylist: () -> Unit,
    private val syncQueue: (ControllerQueuePlan) -> Unit,
    private val controllerQueuePlanner: ControllerQueuePlannerPort,
) {
    private var lastSyncedFingerprint: ControllerQueueFingerprint? = null

    fun remainingMediaItems(): Int {
        val info = controllerQueueInfo()
        return if (info != null && info.mediaItemCount > 0) {
            info.mediaItemCount - info.currentMediaItemIndex - 1
        } else {
            buildControllerQueue(state().playQueue)?.remainingAfterStart ?: 0
        }
    }

    fun clearControllerPlaylist(): Boolean {
        lastSyncedFingerprint = null
        clearPlaylist()
        return true
    }

    fun buildControllerQueue(
        queue: PlayQueue,
        requestedIndex: Int = queue.currentIndex,
    ): ControllerQueuePlan? {
        return controllerQueuePlanner.plan(queue, requestedIndex)
    }

    fun syncPlayerQueue(queue: PlayQueue) {
        if (queue.isEmpty || queue.currentIndex !in queue.songs.indices) return
        val controllerQueue = buildControllerQueue(queue, queue.currentIndex) ?: return
        val fingerprint = controllerQueue.fingerprint()
        if (fingerprint == lastSyncedFingerprint) return
        lastSyncedFingerprint = fingerprint
        syncQueue(controllerQueue)
    }

    private fun ControllerQueuePlan.fingerprint(): ControllerQueueFingerprint {
        return ControllerQueueFingerprint(
            songIds = songs.map { it.id },
            startIndex = startIndex,
        )
    }

    private data class ControllerQueueFingerprint(
        val songIds: List<Int>,
        val startIndex: Int,
    )
}
