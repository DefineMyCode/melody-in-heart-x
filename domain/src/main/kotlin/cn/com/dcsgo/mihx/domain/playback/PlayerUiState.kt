package cn.com.dcsgo.mihx.domain.playback

/**
 * UI-facing playback state. MUST only be constructed inside
 * [ControllerPlaybackStateSynchronizer] (architecture gate A4).
 */
data class PlayerUiState(
    val playbackState: PlaybackState,
    val isPlaying: Boolean,
    val currentMediaId: String?,
    val positionMs: Long,
    val durationMs: Long,
)
