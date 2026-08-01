package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song

/**
 * A fully expanded, ordered play plan ready to be handed to MediaController.
 *
 * [mediaItems] is a bounded window (see [PlaybackWindowPlanner]), not the full queue.
 * [windowStartIndex] is the full-order index of the first item in [mediaItems], used by the
 * window synchronizer to decide when a re-plan is required.
 */
data class ControllerQueuePlan(
    val mediaItems: List<PlannedItem>,
    val currentIndex: Int,
    val windowStartIndex: Int,
) {
    data class PlannedItem(
        val mediaId: String,
        val uri: String?,
        val song: Song,
    )
}
