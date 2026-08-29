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

/**
 * 单项队列循环回绕识别阈值：AUTO discontinuity 且新位置不高于此值，视为回到开头。
 * 旧位置优先按「时长 - 容差」精确判定接近结尾；时长未知时回退 [LOOP_REWIND_MIN_POSITION_MS]
 * 保守阈值（远大于 seek 缓冲抖动，也排除缓冲重连：buffering 恢复从原位置继续，不产生回 0 的 AUTO 间断）。
 */
private const val LOOP_REWIND_END_TOLERANCE_MS = 5_000L
private const val LOOP_REWIND_NEW_POSITION_MAX_MS = 2_000L
private const val LOOP_REWIND_MIN_POSITION_MS = 30_000L

/**
 * 未连接期间允许缓存的待执行动作上限。
 *
 * MediaController 连接失败或长时间未就绪时，UI 每次点击都会追加一个 pending action。
 * 没有上限会持续持有 lambda（及其捕获的歌曲列表）造成内存增长，
 * 因此这里按 FIFO 丢弃最早的动作，并通过 [PlaybackControllerCallbacks.onControllerUnavailable] 上报。
 */
private const val MAX_PENDING_ACTIONS = 64

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

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            // REPEAT_MODE_ALL 下单项队列（秒切/歌单/专辑/歌手只剩一首）自然播完回绕到同一项时，
            // 窗口索引不变（0→0），Media3 不触发 onMediaItemTransition，导致结算逻辑（有效播放+1、
            // 移出秒切列表）永远不执行，且计时器跨循环累计。该场景唯一可见信号是 AUTO discontinuity
            // 的位置回跳，识别后主动按一次播完结算，之后以新会话继续循环计时。
            val current = controller ?: return
            if (isSingleItemLoopRewind(
                    oldIndex = oldPosition.mediaItemIndex,
                    newIndex = newPosition.mediaItemIndex,
                    reason = reason,
                    mediaItemCount = current.mediaItemCount,
                    oldPositionMs = oldPosition.positionMs,
                    newPositionMs = newPosition.positionMs,
                    durationMs = current.duration,
                )
            ) {
                callbacks.onMediaItemEnded(current.currentMediaItem?.mediaId?.toIntOrNull(), false)
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
                    abortPendingActions("播放服务连接失败")
                } catch (e: Exception) {
                    AppLog.error(TAG, "MediaController connection failed", e)
                    abortPendingActions("播放服务连接失败")
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
        if (pendingActions.size >= MAX_PENDING_ACTIONS) {
            // 队列已满说明控制器长时间未就绪：丢弃最早的动作，保证队列有界。
            pendingActions.removeFirst()
            AppLog.warning(
                TAG,
                "Pending action queue overflow (max=$MAX_PENDING_ACTIONS), dropped the oldest action",
            )
            callbacks.onControllerUnavailable(1, "播放服务尚未就绪")
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

    /**
     * 连接失败时清空待执行队列并回调错误。
     *
     * 若继续保留这些动作，它们会在下一次连接成功时被批量重放，
     * 产生用户早已放弃的播放/切歌行为，因此这里选择丢弃并显式告知上层。
     */
    private fun abortPendingActions(reason: String) {
        val dropped = pendingActions.size
        pendingActions.clear()
        if (dropped > 0) {
            AppLog.warning(TAG, "Dropped $dropped pending MediaController action(s): $reason")
        }
        callbacks.onControllerUnavailable(dropped, reason)
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

/**
 * 判断某次 position 间断是否为「单项队列的循环回绕」：
 * REPEAT_MODE_ALL 下队列只有一首歌时，自然播完从尾部回到同一项开头，
 * 索引不变（0→0），不会触发 onMediaItemTransition，只能靠 AUTO discontinuity
 * 且位置明显回跳（接近结尾 → 接近 0）来识别。
 *
 * @param oldIndex/newIndex 间断前后的媒体项索引
 * @param reason [androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION]
 * @param mediaItemCount 当前队列项数（必须恰为 1）
 * @param oldPositionMs/newPositionMs 间断前后的播放位置
 * @param durationMs 当前媒体项时长；>0 时按其精确判定"接近结尾"，未知（-1）时回退保守阈值
 */
internal fun isSingleItemLoopRewind(
    oldIndex: Int,
    newIndex: Int,
    reason: Int,
    mediaItemCount: Int,
    oldPositionMs: Long,
    newPositionMs: Long,
    durationMs: Long,
): Boolean {
    val nearEndOfTrack = if (durationMs > 0L) {
        oldPositionMs >= durationMs - LOOP_REWIND_END_TOLERANCE_MS
    } else {
        oldPositionMs >= LOOP_REWIND_MIN_POSITION_MS
    }
    return reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
        mediaItemCount == 1 &&
        oldIndex == 0 &&
        newIndex == 0 &&
        newPositionMs <= LOOP_REWIND_NEW_POSITION_MAX_MS &&
        nearEndOfTrack
}
