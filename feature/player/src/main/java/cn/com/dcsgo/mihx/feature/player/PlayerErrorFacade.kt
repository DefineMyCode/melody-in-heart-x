package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

class PlayerErrorFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val startQueuePlayback: (PlayQueue, Int) -> Boolean,
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) {
    fun clearError() {
        updateState { it.copy(errorMessage = null) }
    }

    fun playFromQueue(queue: PlayQueue, index: Int) {
        val song = queue.songs.getOrNull(index) ?: return
        if (isPlayable(song)) {
            startQueuePlayback(queue, index)
        } else {
            updateState { it.copy(errorMessage = "「${song.title}」的本地文件不存在，无法播放") }
        }
    }
}
