package cn.com.dcsgo.mihx.domain.playback

data class ControllerQueueSnapshot(
    val mediaItemCount: Int,
    val currentMediaItemIndex: Int,
)

data class PlaybackControllerCallbacks(
    val onIsPlayingChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    val onMediaItemEnded: (songId: Int?) -> Unit,
    val onPlaybackSnapshot: (ControllerPlaybackSnapshot) -> Unit,
    val onPlaybackEnded: () -> Unit,
)

interface PlaybackControllerPort : PlaybackSessionController {
    fun startService()
    fun connect(onConnected: (ControllerPlaybackSnapshot) -> Unit)
    fun snapshot(): ControllerPlaybackSnapshot?
    fun queueInfo(): ControllerQueueSnapshot?
    fun clearPlaylist()
    fun playPrevious()
    fun playNext()
    fun syncQueue(plan: ControllerQueuePlan)
    fun release()
}

fun interface PlaybackControllerPortFactory {
    fun create(callbacks: PlaybackControllerCallbacks): PlaybackControllerPort
}
