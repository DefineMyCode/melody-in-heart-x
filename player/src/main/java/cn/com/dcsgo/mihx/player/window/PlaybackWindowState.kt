package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.Song

data class PlaybackWindowState(
    val songs: List<Song>,
    val controllerStartIndex: Int,
    val fullQueueStartIndex: Int,
    val fullQueueCurrentIndex: Int,
) {
    val fullQueueEndExclusive: Int = fullQueueStartIndex + songs.size

    fun containsFullQueueIndex(index: Int): Boolean {
        return index in fullQueueStartIndex until fullQueueEndExclusive
    }
}
