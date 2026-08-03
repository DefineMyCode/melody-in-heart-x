package cn.com.dcsgo.mihx.data.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.PerformanceTrace
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.ControllerQueueSnapshot
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerCallbacks
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPort
import java.util.concurrent.ExecutionException

private const val TAG = "PlaybackController"

class PlaybackController(
    private val context: Context,
    private val serviceClass: Class<*>,
    private val callbacks: PlaybackControllerCallbacks,
) : PlaybackControllerPort {
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var lastSyncedQueueFingerprint: ControllerQueueFingerprint? = null
    private val pendingActions = ArrayDeque<(MediaController) -> Unit>()
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            callbacks.onIsPlayingChanged(
                isPlaying,
                controller?.playbackState == Player.STATE_BUFFERING,
            )
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                callbacks.onMediaItemEnded(null)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem != null && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                callbacks.onMediaItemEnded(mediaItem.mediaId.toIntOrNull())
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            callbacks.onPlaybackSnapshot(player.toPlaybackSnapshot())
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                callbacks.onPlaybackEnded()
            }
        }
    }

    override fun startService() {
        context.startService(Intent(context, serviceClass))
    }

    override fun connect(onConnected: (ControllerPlaybackSnapshot) -> Unit) {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, serviceClass)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val connectedController = future.get()
                    controller = connectedController
                    connectedController.addListener(listener)
                    drainPendingActions(connectedController)
                    onConnected(connectedController.toPlaybackSnapshot())
                    AppLog.info(TAG, "MediaController connected")
                } catch (e: ExecutionException) {
                    AppLog.error(TAG, "MediaController connection failed", e.cause ?: e)
                } catch (e: Exception) {
                    AppLog.error(TAG, "MediaController connection failed", e)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun controllerOrNull(): MediaController? = controller

    override fun snapshot(): ControllerPlaybackSnapshot? {
        return controller?.toPlaybackSnapshot()
    }

    override fun queueInfo(): ControllerQueueSnapshot? {
        return controller?.let { controller ->
            ControllerQueueSnapshot(
                mediaItemCount = controller.mediaItemCount,
                currentMediaItemIndex = controller.currentMediaItemIndex,
            )
        }
    }

    override val hasCurrentMediaItem: Boolean
        get() = controller?.currentMediaItem != null

    override val isPlaying: Boolean?
        get() = controller?.isPlaying

    override fun currentPositionMs(fallback: Long): Long {
        return controller?.currentPosition?.coerceAtLeast(0L) ?: fallback.coerceAtLeast(0L)
    }

    override fun durationMs(fallback: Long): Long {
        return controller?.duration
            ?.takeIf { it != C.TIME_UNSET }
            ?.coerceAtLeast(0L)
            ?: fallback.coerceAtLeast(0L)
    }

    fun runWhenConnected(action: (MediaController) -> Unit): Boolean {
        controller?.let {
            action(it)
            return true
        }
        pendingActions.addLast(action)
        return false
    }

    override fun pause() {
        runWhenConnected { it.pause() }
    }

    override fun play() {
        runWhenConnected { it.play() }
    }

    override fun clearPlaylist() {
        runWhenConnected { controller ->
            lastSyncedQueueFingerprint = null
            controller.stop()
            controller.clearMediaItems()
        }
    }

    override fun playPrevious() {
        runWhenConnected { controller ->
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekTo(0)
            }
        }
    }

    override fun playNext() {
        runWhenConnected { controller ->
            val startedAt = PerformanceTrace.nowMs()
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (controller.mediaItemCount > 0) {
                controller.seekToDefaultPosition(0)
            }
            PerformanceTrace.log(
                operation = "play_next_command",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = mapOf(
                    "mediaItemCount" to controller.mediaItemCount,
                    "currentIndex" to controller.currentMediaItemIndex,
                ),
            )
        }
    }

    override fun seekTo(positionMs: Long) {
        runWhenConnected { it.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    override fun playQueue(plan: ControllerQueuePlan) {
        runWhenConnected { controller ->
            val startedAt = PerformanceTrace.nowMs()
            lastSyncedQueueFingerprint = plan.fingerprint()
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.setMediaItems(plan.toMediaItems(), plan.startIndex, 0L)
            controller.prepare()
            controller.play()
            PerformanceTrace.log(
                operation = "controller_play_queue",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = plan.performanceMetadata(),
            )
        }
    }

    override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) {
        runWhenConnected { controller ->
            val startedAt = PerformanceTrace.nowMs()
            lastSyncedQueueFingerprint = plan.fingerprint()
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.setMediaItems(plan.toMediaItems(), plan.startIndex, positionMs.coerceAtLeast(0L))
            controller.prepare()
            PerformanceTrace.log(
                operation = "controller_prepare_queue",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = plan.performanceMetadata(),
            )
        }
    }

    override fun playSingle(song: Song): Boolean {
        val mediaItem = SongMediaItemMapper.toMediaItem(song) ?: return false
        runWhenConnected { controller ->
            lastSyncedQueueFingerprint = null
            controller.repeatMode = Player.REPEAT_MODE_OFF
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        return true
    }

    override fun syncQueue(plan: ControllerQueuePlan) {
        runWhenConnected { controller ->
            val fingerprint = plan.fingerprint()
            if (fingerprint == lastSyncedQueueFingerprint) return@runWhenConnected
            val startedAt = PerformanceTrace.nowMs()
            val positionMs = controller.currentPosition.coerceAtLeast(0L)
            val wasPlaying = controller.isPlaying
            lastSyncedQueueFingerprint = fingerprint
            controller.repeatMode = Player.REPEAT_MODE_ALL
            controller.setMediaItems(plan.toMediaItems(), plan.startIndex, positionMs)
            controller.prepare()
            if (wasPlaying) {
                controller.play()
            }
            PerformanceTrace.log(
                operation = "controller_sync_queue",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = plan.performanceMetadata() + ("wasPlaying" to wasPlaying),
            )
        }
    }

    override fun release() {
        controller?.removeListener(listener)
        controller = null
        pendingActions.clear()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun drainPendingActions(controller: MediaController) {
        while (pendingActions.isNotEmpty()) {
            try {
                pendingActions.removeFirst().invoke(controller)
            } catch (e: Exception) {
                AppLog.error(TAG, "Pending MediaController action failed", e)
            }
        }
    }

    private fun ControllerQueuePlan.toMediaItems(): List<MediaItem> {
        return songs.mapNotNull { SongMediaItemMapper.toMediaItem(it) }
    }

    private fun ControllerQueuePlan.performanceMetadata(): Map<String, Any?> {
        return mapOf(
            "songCount" to songs.size,
            "startIndex" to startIndex,
        )
    }

    private fun ControllerQueuePlan.fingerprint(): ControllerQueueFingerprint {
        return ControllerQueueFingerprint(
            songIds = songs.map { it.id },
            startIndex = startIndex,
        )
    }

    private fun Player.toPlaybackSnapshot(): ControllerPlaybackSnapshot {
        return ControllerPlaybackSnapshot(
            mediaId = currentMediaItem?.mediaId,
            isPlaying = isPlaying,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            currentPositionMs = currentPosition,
            durationMs = duration.takeIf { it != C.TIME_UNSET },
        )
    }

    private data class ControllerQueueFingerprint(
        val songIds: List<Int>,
        val startIndex: Int,
    )
}
