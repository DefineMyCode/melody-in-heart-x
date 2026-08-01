package cn.com.dcsgo.mihx.domain.playback

/** Immutable snapshot emitted by the MediaController listener. */
data class ControllerPlaybackSnapshot(
    val isPlaying: Boolean,
    val currentMediaId: String?,
    val positionMs: Long,
    val durationMs: Long,
    val playbackState: Int,
    val buffering: Boolean,
)
