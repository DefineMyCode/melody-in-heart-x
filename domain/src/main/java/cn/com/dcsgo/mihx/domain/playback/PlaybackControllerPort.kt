package cn.com.dcsgo.mihx.domain.playback

data class ControllerQueueSnapshot(
    val mediaItemCount: Int,
    val currentMediaItemIndex: Int,
)

data class PlaybackControllerCallbacks(
    val onIsPlayingChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
    val onMediaItemEnded: (songId: Int?, wrapped: Boolean) -> Unit,
    val onPlaybackSnapshot: (ControllerPlaybackSnapshot) -> Unit,
    val onPlaybackEnded: () -> Unit,
    /** 播放源错误（如本地文件缺失），传当前媒体项对应的歌曲 id */
    val onPlayerError: (songId: Int?) -> Unit = {},
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
