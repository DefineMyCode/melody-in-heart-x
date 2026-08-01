package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.PlaybackState

/** UI state for the 播放 screen. */
data class PlayerUiState(
    val isLoading: Boolean = false,
    val playbackState: PlaybackState = PlaybackState.IDLE,
    val isPlaying: Boolean = false,
    val currentMediaId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val sliderPositionMs: Long = 0L,
    val isDragging: Boolean = false,
    val queue: PlayQueue = PlayQueue(emptyList(), 0, PlayMode.SEQUENTIAL, emptyList()),
    val highlightIndex: Int = 0,
) {
    companion object {
        /** Default (empty) state. Construction lives here per architecture gate [A4]. */
        val empty: PlayerUiState = PlayerUiState()
    }
}
