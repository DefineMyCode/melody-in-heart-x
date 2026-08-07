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
    private var lastCurrentMediaItemIndex: Int = C.INDEX_UNSET
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
                callbacks.onMediaItemEnded(null, false)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val currentController = controller
            val newIndex = currentController?.currentMediaItemIndex ?: C.INDEX_UNSET
            // 回绕后剩余数量会重新变大，仅靠剩余阈值判断会漏掉补队列，导致无限播放枯竭，
            // 因此这里用索引判断回绕并传给 onMediaItemEnded（详见 isMediaItemWrap）。
            val wrapped = isMediaItemWrap(
                previousIndex = lastCurrentMediaItemIndex,
                newIndex = newIndex,
                mediaItemCount = currentController?.mediaItemCount ?: 0,
            )
            lastCurrentMediaItemIndex = newIndex

            // 用户导航型切歌（自然结束 AUTO、手动下一首/上一首 SEEK、单曲重复 REPEAT）
            // 都要走 onMediaItemEnded，否则耳机/锁屏/通知栏手动切歌时无限播放不会补队列。
            // PLAYLIST_CHANGED 是应用自身重建窗口产生的，不需要触发。
            if (mediaItem != null &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                callbacks.onMediaItemEnded(
                    mediaItem.mediaId.toIntOrNull(),
                    wrapped,
                )
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

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            AppLog.error(TAG, "onPlayerError: ${error.errorCodeName} ${error.message}", error)
            callbacks.onPlayerError(controller?.currentMediaItem?.mediaId?.toIntOrNull())
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

/**
 * 判断某次媒体项切换是否为窗口尾部回绕。
 *
 * 回绕发生在「上一首处于窗口最后一首、新位置回到索引 0」时。注意 Media3 对
 * `REPEAT_MODE_ALL` 下 `seekToNextMediaItem()` 从尾部回绕到开头，仍以
 * `MEDIA_ITEM_TRANSITION_REASON_SEEK` 上报；`MEDIA_ITEM_TRANSITION_REASON_REPEAT`
 * 只表示 `REPEAT_MODE_ONE` 单曲重复，不会在回绕时触发，因此必须用索引而非原因判断。
 *
 * @param previousIndex 切换前的 `currentMediaItemIndex`；无当前项时为 [C.INDEX_UNSET]
 * @param newIndex      切换后的 `currentMediaItemIndex`
 * @param mediaItemCount 切换后的队列项数
 */
internal fun isMediaItemWrap(
    previousIndex: Int,
    newIndex: Int,
    mediaItemCount: Int,
): Boolean {
    return newIndex == 0 &&
        previousIndex != C.INDEX_UNSET &&
        previousIndex >= mediaItemCount - 1 &&
        previousIndex > newIndex
}
