package cn.com.dcsgo.mihx.feature.player

import android.os.SystemClock
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinator
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreResult
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PlayerPersistenceGraph(
    playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val syncPlaybackState: () -> Unit,
    private val currentPlaybackPositionMs: () -> Long,
    private val prepareControllerQueue: (PlayQueue, Int, Long) -> Boolean,
    /** 是否存在正在播放的 live session: 若是，restorePlaybackState 不覆盖 live player */
    private val hasLiveSession: () -> Boolean,
    private val log: (String) -> Unit,
    /** 恢复可播判定（默认按 uri 非空）；测试可注入不依赖 android.net.Uri 的判据 */
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) {
    /**
     * 持久化写入队列：**单消费者 + 无界通道**，保证写操作严格 FIFO。
     *
     * 评审 2026-10-27 · N-1：原先「清空快照」是同步阻塞调用、「写快照」是
     * `scope.launch(dispatchers.io)` 异步调用，两者混用时序靠「同步那个先跑完」
     * 保证。全异步化后若都挂到多线程的 `Dispatchers.IO` 上，**顺序不再保证**——
     * 可能出现「快照刚写完又被清掉」，队列与进度丢失。
     *
     * 用「通道 + 单消费者」而不是 `limitedParallelism(1)`：前者与调度器无关
     * （`Dispatchers.Unconfined` 不支持 `limitedParallelism`），且天然 FIFO。
     *
     * 该消费者**不挂在 ViewModel 生命周期上**：`onCleared` 时排队的最后一次写入
     * 仍能落盘（原同步实现靠阻塞主线程来保证写入完成，现改为「队列保活直到写完」）。
     */
    private val persistCommands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val persistScope = CoroutineScope(SupervisorJob() + dispatchers.io)

    init {
        persistScope.launch {
            for (command in persistCommands) {
                try {
                    command()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // 单条写失败不应终止消费者，否则后续所有写入永久排队不执行
                    log("persist command failed: ${t.message}")
                }
            }
        }
    }

    /** 把一次持久化写入排入 FIFO 队列（不阻塞调用线程）。 */
    private fun enqueuePersist(command: suspend () -> Unit) {
        persistCommands.trySend(command)
    }

    private val playbackStateStore by lazy {
        playbackStateStorageFactory.create()
    }
    private val playbackRestoreCoordinator: PlaybackRestoreCoordinator by lazy {
        PlaybackRestoreCoordinator(playbackStateStore::restore, isPlayable)
    }
    private val persistenceFacade: PlayerPersistenceFacade by lazy {
        PlayerPersistenceFacade(
            state = state,
            updateState = updateState,
            playbackStateStore = playbackStateStore,
            playbackRestoreCoordinator = playbackRestoreCoordinator,
            currentPlaybackPositionMs = currentPlaybackPositionMs,
            prepareControllerQueue = prepareControllerQueue,
            launchIo = { task -> enqueuePersist(task) },
            log = log,
        )
    }
    private val playbackStateAutosaver = PlayerPlaybackStateAutosaver(
        currentTimeMs = { SystemClock.elapsedRealtime() },
        syncPlaybackState = syncPlaybackState,
        savePlaybackState = { positionMs -> savePlaybackStateAsync(positionMs) },
    )

    /** controller 连接尝试是否已成功结束（MediaController 可用，状态可查询） */
    private var controllerReady = false

    /** IO 线程读出的快照恢复结果，等待 [controllerReady] 后统一决策应用 */
    private var pendingRestore: PlaybackRestoreResult? = null

    fun savePlaybackStateAsync(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackStateAsync(positionMs)
    }

    /** 排队写入快照（不阻塞调用线程）。 */
    fun savePlaybackState(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackStateAsync(positionMs)
    }

    /** 排队清空快照（不阻塞调用线程）。与写入同一条 FIFO 队列，顺序得到保证。 */
    fun clearPlaybackState() {
        enqueuePersist { persistenceFacade.clearPlaybackState() }
    }

    /**
     * MediaController 连接成功后由外层回调（此时已同步过一次 controller snapshot）。
     *
     * restore 的最终决策必须等 controller 状态确定之后再做：在此之前判断
     * 「是否存在 live 会话」只能靠猜（position > 0 / queueInfo 为 null 的竞态），
     * 猜错就会拿 DataStore 快照去覆盖服务端正在播放的会话。
     */
    fun onControllerReady() {
        controllerReady = true
        maybeApplyRestore()
    }

    fun restorePlaybackState() {
        // DataStore 读取/解码（内部 runBlocking）放 IO，启动路径不被阻塞。
        // 读取与 controller 连接并行：谁先完成都等另一方，再在 maybeApplyRestore 里
        // 依据 controller 真实状态做「完整恢复 or 仅恢复 UI 队列」的决策，消除竞态。
        scope.launch(dispatchers.io) {
            // 兜底：协调器/解码任何未预期的异常都不该静默吞掉恢复，留日志可查（2026-10-06）。
            val result = runCatching { persistenceFacade.restorePlaybackState() }
                .onFailure { e -> log("restore read FAILED: ${e.message}") }
                .getOrNull()
            if (result != null) {
                withContext(dispatchers.main) {
                    pendingRestore = result
                    maybeApplyRestore()
                }
            }
        }
    }

    private fun maybeApplyRestore() {
        if (!controllerReady) return
        val result = pendingRestore ?: return
        pendingRestore = null

        // controller 已连接，queueInfo 反映真实媒体项：非空即 live session。
        // live session：快照只回填 UI 队列影子（controller 同步不填 songs，整个跳过会留空队列），
        // 真实 currentSong/isPlaying/位置由 controller snapshot 同步；controller 队列绝不覆盖。
        // 无 live session（冷启动/服务端空）：快照完整恢复（UI + 灌 controller，不自动播放）。
        val live = hasLiveSession()
        log(
            "restorePlaybackState decision: liveSession=$live, snapshot=${result.queue.songs.size} songs, " +
                "index=${result.queue.currentIndex}, mode=${result.queue.playMode.name}" +
                if (live) " -> UI queue only" else " -> full restore"
        )
        persistenceFacade.applyRestoreResult(result, restoreController = !live)
    }

    fun onPlaybackPosition(positionMs: Long) {
        playbackStateAutosaver.onPlaybackPosition(positionMs)
    }
}
