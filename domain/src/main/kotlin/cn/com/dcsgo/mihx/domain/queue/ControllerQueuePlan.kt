package cn.com.dcsgo.mihx.domain.queue

/** A fully expanded, ordered play plan ready to be handed to MediaController. */
data class ControllerQueuePlan(
    val mediaItems: List<PlannedItem>,
    val currentIndex: Int,
) {
    data class PlannedItem(val mediaId: String, val uri: String?)
}
