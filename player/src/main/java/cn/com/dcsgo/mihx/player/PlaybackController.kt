package cn.com.dcsgo.mihx.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.ArtworkUriAccess
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

    /**
     * 队列结构镜像回执追踪：任何队列修改命令（setMediaItems/remove/add）发出后置 true，
     * 收到服务端 onTimelineChanged 回执后清 false。为 true 期间本地 timeline 镜像可能过期，
     * [syncQueue] 的增量路径必须等回执到达后才能决策，否则 remove/add 的索引会错位下发。
     */
    private var pendingQueueMutation = false

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
            // 排查"开头卡顿重复"：客户端视角位置对（含 syncQueue 重建后的恢复点），
            // 与服务端 AppMediaSessionService.position_discontinuity server 日志对照。
            AppLog.info(
                TAG,
                "position_discontinuity client: oldIndex=${oldPosition.mediaItemIndex} oldMs=${oldPosition.positionMs}" +
                    " newIndex=${newPosition.mediaItemIndex} newMs=${newPosition.positionMs} reason=$reason",
            )
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

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // 队列修改命令的服务端回执：镜像已刷新，允许下一次增量队列同步决策。
            pendingQueueMutation = false
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
        // 2026-09-03 ANR 回归修复（M-12 整改引入）：
        // startForegroundService() 强制要求服务 5s 内调用 startForeground()，
        // 而 MediaSessionService 只在 player 有媒体项/激活播放时才发布通知并
        // startForeground()——冷启动空会话永不触发 → 系统判定 FGS 超时 ANR
        // （真机日志 "did not then call Service.startForeground()"）。
        // 本方法唯一调用链是 UI 冷启动（Activity 前台），startService 合法且无
        // FGS 契约；本项目亦无 WorkManager 等后台拉起路径，即使出现，
        // connect() 的 SessionToken 机制同样能拉起服务。
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
            pendingQueueMutation = true
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

    /**
     * 用户主动播放入口（点歌 / 切队列 / 错误恢复重播）。
     *
     * **禁止在此加 live session 守卫**：只要服务在播 mediaItemCount 就 > 0，
     * 加了会让所有用户点歌请求被静默吞掉、服务继续放旧歌。
     * 保护 live session 的职责属于恢复路径（[prepareQueue] 与其上层调用方）。
     */
    override fun playQueue(plan: ControllerQueuePlan) {
        runWhenConnected { controller ->
            val startedAt = PerformanceTrace.nowMs()
            lastSyncedQueueFingerprint = plan.fingerprint()
            pendingQueueMutation = true
            controller.repeatMode = Player.REPEAT_MODE_ALL
            val mediaItems = plan.toMediaItems()
            grantArtworkRead(mediaItems)
            controller.setMediaItems(mediaItems, plan.startIndex, 0L)
            controller.prepare()
            controller.play()
            PerformanceTrace.log(
                operation = "controller_play_queue",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = plan.performanceMetadata(),
            )
        }
    }

    /**
     * 发布媒体项**之前**给其中的封面 URI 授予跨进程读权限。
     *
     * 封面 URI 走 FileProvider 的 `content://`（`:data` 的 `AlbumArtProvider` 产出）。
     * 平台的 `MediaSessionRecord` 只**校验**该 URI 是否可被授予，**不负责授权**；
     * 真正让 SystemUI 媒体控制卡片 / 平台媒体通知 / 车机等跨进程消费方读得到封面，
     * 得由 App 显式 `grantUriPermission()`，否则对方会拿到
     * `SecurityException: Permission Denial: reading ... requires the provider be exported,
     *  or grantUriPermission()`。
     *
     * **为什么必须在这里（下发之前）**：跨进程消费方一收到 metadata 推送就会去读封面，
     * 等 `onMediaItemTransition` 再授权就晚了（读取先于授权 → 首帧封面空白）。
     * 这里补的是「存量 URI」的授权 —— 本进程没铸造过、来自上一次运行写进 Room 的封面，
     * `:data` 的铸造时授权覆盖不到它们。
     *
     * 按 URI 去重：同一专辑的 N 首歌共享同一张内容哈希封面 → 同一个 URI。
     */
    private fun grantArtworkRead(mediaItems: List<MediaItem>) {
        ArtworkUriAccess.grantRead(context, mediaItems.mapNotNull { it.mediaMetadata?.artworkUri })
    }

    /**
     * 恢复路径专用（唯一业务调用方是 [PlayerPersistenceFacade.applyRestoreResult]），
     * 用于把 DataStore 里的队列与位置重新灌回控制器但**不自动播放**。
     *
     * live session 守卫只在**这里**成立：恢复时若服务端已有媒体项，重建队列会覆盖
     * 正在播放的会话（进度回退到快照位置），此时只应补齐 UI 侧队列数据，
     * 真实位置交给 connect 后的 syncControllerPlaybackState 同步。
     */
    override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) {
        runWhenConnected { controller ->
            if (controller.mediaItemCount > 0) {
                AppLog.info(TAG, "prepareQueue skipped: live session active (items=${controller.mediaItemCount})")
                return@runWhenConnected
            }
            val startedAt = PerformanceTrace.nowMs()
            lastSyncedQueueFingerprint = plan.fingerprint()
            pendingQueueMutation = true
            controller.repeatMode = Player.REPEAT_MODE_ALL
            val mediaItems = plan.toMediaItems()
            grantArtworkRead(mediaItems)
            controller.setMediaItems(mediaItems, plan.startIndex, positionMs.coerceAtLeast(0L))
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
            pendingQueueMutation = true
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
            val controllerMediaIds = List(controller.mediaItemCount) { index ->
                controller.getMediaItemAt(index).mediaId.toIntOrNull()
            }
            val planSongIds = plan.songs.map { it.id }
            val incremental = if (pendingQueueMutation) {
                null
            } else {
                planIncrementalQueueSync(
                    controllerMediaIds = controllerMediaIds,
                    controllerCurrentIndex = controller.currentMediaItemIndex,
                    controllerPlaybackState = controller.playbackState,
                    planSongIds = planSongIds,
                    planStartIndex = plan.startIndex,
                )
            }
            lastSyncedQueueFingerprint = fingerprint
            if (incremental != null) {
                // 增量同步：当前播放项不动（无 position 重置、无重缓冲、无 playWhenReady 变化），
                // 仅按窗口滚动裁前部 / 队列尾部增减调整。触发源（refill 补队列、快照同步补窗口）
                // 大多发生在切歌刚起播的瞬间，全量 setMediaItems 会把新歌拉回过期位置重播 1~2 秒。
                if (incremental.trimCount > 0 ||
                    incremental.commonCount < controllerMediaIds.size - incremental.trimCount ||
                    incremental.commonCount < planSongIds.size
                ) {
                    pendingQueueMutation = true
                }
                controller.repeatMode = Player.REPEAT_MODE_ALL
                if (incremental.trimCount > 0) {
                    controller.removeMediaItems(0, incremental.trimCount)
                }
                val retainedCount = controllerMediaIds.size - incremental.trimCount
                if (retainedCount > incremental.commonCount) {
                    controller.removeMediaItems(incremental.commonCount, retainedCount)
                }
                if (incremental.commonCount < planSongIds.size) {
                    val appended = plan.songs
                        .subList(incremental.commonCount, plan.songs.size)
                        .mapNotNull { SongMediaItemMapper.toMediaItem(it) }
                    if (appended.isNotEmpty()) {
                        grantArtworkRead(appended)
                        controller.addMediaItems(incremental.commonCount, appended)
                    }
                }
                PerformanceTrace.log(
                    operation = "controller_sync_queue",
                    elapsedMs = PerformanceTrace.nowMs() - startedAt,
                    metadata = plan.performanceMetadata() + mapOf(
                        "mode" to "incremental",
                        "trimCount" to incremental.trimCount,
                        "appendCount" to (planSongIds.size - incremental.commonCount),
                    ),
                )
                return@runWhenConnected
            }
            val positionMs = controller.currentPosition.coerceAtLeast(0L)
            val wasPlaying = controller.isPlaying
            pendingQueueMutation = true
            controller.repeatMode = Player.REPEAT_MODE_ALL
            val mediaItems = plan.toMediaItems()
            grantArtworkRead(mediaItems)
            controller.setMediaItems(mediaItems, plan.startIndex, positionMs)
            controller.prepare()
            if (wasPlaying) {
                controller.play()
            }
            PerformanceTrace.log(
                operation = "controller_sync_queue",
                elapsedMs = PerformanceTrace.nowMs() - startedAt,
                metadata = plan.performanceMetadata() + mapOf(
                    "mode" to "rebuild",
                    "wasPlaying" to wasPlaying,
                    // 排查"开头卡顿重复"：restoreMs = 传给 setMediaItems 的恢复位置（读取时刻的客户端缓存值），
                    // 对照紧邻的服务端 position_discontinuity server 日志 newMs（实际生效恢复点），
                    // 两者与听感已播进度的差值即回跳量。
                    "restoreMs" to positionMs,
                    "currentMediaId" to (controller.currentMediaItem?.mediaId ?: "null"),
                ),
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
/**
 * 增量队列同步方案：controller 现有队列与新窗口存在公共对齐段、且当前播放项保持不变时，
 * 用 removeMediaItems/addMediaItems 调整队列，替代 setMediaItems 全量重建。
 *
 * 全量重建会重置解码与输出（丢当前位置、重缓冲 ~200ms+），在切歌刚起播的瞬间执行时，
 * 恢复位置还可能读到过期的小值，产生"开头重播 1~2 秒"；增量路径完全不动当前播放项，
 * 从根上消除该回跳窗口。返回 null 表示无法增量（队列形状复杂变化），调用方应回退全量重建。
 *
 * @param controllerMediaIds   controller 本地镜像中各媒体项的 songId（mediaId 转换）
 * @param controllerCurrentIndex 当前播放项在 controller 队列中的索引
 * @param controllerPlaybackState 当前播放状态：仅 READY / BUFFERING 允许增量
 *                               （IDLE 尚未准备、ENDED 需要 prepare 复位，均走全量）
 * @param planSongIds          新窗口的歌曲 id 序列
 * @param planStartIndex       当前播放项在新窗口中的索引
 */
internal data class IncrementalQueueSyncPlan(
    /** 从 controller 队列头部移除的项数（窗口前部滚动，全部位于当前播放项之前） */
    val trimCount: Int,
    /** controller 队列与新窗口的公共项数：裁剪后比它多的尾部要移除，新窗口比它多的尾部要追加 */
    val commonCount: Int,
)

internal fun planIncrementalQueueSync(
    controllerMediaIds: List<Int?>,
    controllerCurrentIndex: Int,
    controllerPlaybackState: Int,
    planSongIds: List<Int>,
    planStartIndex: Int,
): IncrementalQueueSyncPlan? {
    if (controllerMediaIds.isEmpty() || planSongIds.isEmpty()) return null
    if (controllerCurrentIndex !in controllerMediaIds.indices) return null
    if (planStartIndex !in planSongIds.indices) return null
    if (controllerPlaybackState != Player.STATE_READY &&
        controllerPlaybackState != Player.STATE_BUFFERING
    ) {
        return null
    }
    val currentId = controllerMediaIds[controllerCurrentIndex]
    if (currentId == null || currentId != planSongIds[planStartIndex]) return null

    // 前部对齐：窗口中心推进后新窗口裁掉了 controller 的前 trim 项（同一业务队列的连续切片错位）。
    // trim 不得超过当前项索引——移除范围绝不允许包含当前播放项。
    for (trim in 0..controllerCurrentIndex) {
        val common = minOf(controllerMediaIds.size - trim, planSongIds.size)
        var aligned = true
        for (i in 0 until common) {
            if (controllerMediaIds[trim + i] != planSongIds[i]) {
                aligned = false
                break
            }
        }
        // 当前项必须保留在公共段内：队列尾部收缩时当前项若位于将被移除的尾部（如删歌把当前歌
        // 挤出窗口），移除命令会打断播放，必须回退全量路径处理。
        if (aligned && controllerCurrentIndex - trim < common) {
            return IncrementalQueueSyncPlan(trimCount = trim, commonCount = common)
        }
    }
    return null
}
