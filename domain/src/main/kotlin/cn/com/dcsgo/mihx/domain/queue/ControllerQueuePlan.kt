package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song

/**
 * A fully expanded, ordered play plan ready to be handed to MediaController.
 *
 * [mediaItems] is a bounded window (see [PlaybackWindowPlanner]), not the full queue.
 * [windowStartIndex] until [windowEndIndexExclusive] is the half-open slice of the *unfiltered*
 * full order this window covers; the window synchronizer uses that range both to decide when a
 * re-plan is required and to diff two consecutive windows for an incremental slide.
 */
data class ControllerQueuePlan(
    val mediaItems: List<PlannedItem>,
    val currentIndex: Int,
    val windowStartIndex: Int,
    val windowEndIndexExclusive: Int,
) {
    data class PlannedItem(
        val mediaId: String,
        val uri: String?,
        val song: Song,
    )
}
