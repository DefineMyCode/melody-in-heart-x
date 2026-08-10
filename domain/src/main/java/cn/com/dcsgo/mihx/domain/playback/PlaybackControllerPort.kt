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
    /**
     * 播放服务不可用：连接失败，或未连接期间待执行动作堆积超过上限。
     *
     * @param droppedActionCount 被丢弃的待执行动作数量（0 表示仅通知不可用）
     * @param reason 简要原因，供日志与用户提示使用
     */
    val onControllerUnavailable: (droppedActionCount: Int, reason: String) -> Unit = { _, _ -> },
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
