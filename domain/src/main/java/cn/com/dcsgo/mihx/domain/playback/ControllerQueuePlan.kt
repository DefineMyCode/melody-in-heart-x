package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.Song

data class ControllerQueuePlan(
    val songs: List<Song>,
    val startIndex: Int,
) {
    val remainingAfterStart: Int
        get() = songs.size - startIndex - 1
}
