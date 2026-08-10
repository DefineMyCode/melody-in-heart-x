package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerCallbacks
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPortFactory

private const val TAG = "PlayerMediaControllerGraph"

internal class PlayerMediaControllerGraph(
    playbackControllerPortFactory: PlaybackControllerPortFactory,
    private val controllerStateAdapter: PlayerControllerStateAdapter,
    private val handleMediaItemEnded: (Int?, Boolean) -> Unit,
    private val handlePlaybackEnded: () -> Unit,
    private val handlePlayerError: (Int?) -> Unit = {},
    private val handleControllerUnavailable: (Int, String) -> Unit = { _, _ -> },
) {
    val playbackController = playbackControllerPortFactory.create(
        PlaybackControllerCallbacks(
            onIsPlayingChanged = controllerStateAdapter::handleIsPlayingChanged,
            onMediaItemEnded = handleMediaItemEnded,
            onPlaybackSnapshot = controllerStateAdapter::sync,
            onPlaybackEnded = handlePlaybackEnded,
            onPlayerError = handlePlayerError,
            onControllerUnavailable = handleControllerUnavailable,
        )
    )

    fun startService() {
        playbackController.startService()
    }

    fun connect() {
        playbackController.connect(controllerStateAdapter::sync)
    }

    fun controllerQueueInfo(): ControllerQueueInfo? {
        return playbackController.queueInfo()?.let { controller ->
            ControllerQueueInfo(
                mediaItemCount = controller.mediaItemCount,
                currentMediaItemIndex = controller.currentMediaItemIndex,
            )
        }
    }

    fun syncCurrentPlaybackState() {
        playbackController.snapshot()?.let(controllerStateAdapter::sync)
    }

    fun release() {
        playbackController.release()
    }

    fun logInfo(message: String) {
        AppLog.info(TAG, message)
    }

    fun logError(message: String, error: Throwable) {
        AppLog.error(TAG, message, error)
    }
}
