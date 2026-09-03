package cn.com.dcsgo.mihx.feature.player

import android.os.SystemClock
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinator
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreResult
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import kotlinx.coroutines.CoroutineScope
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
) {
    private val playbackStateStore by lazy {
        playbackStateStorageFactory.create()
    }
    private val playbackRestoreCoordinator: PlaybackRestoreCoordinator by lazy {
        PlaybackRestoreCoordinator(playbackStateStore::restore)
    }
    private val persistenceFacade: PlayerPersistenceFacade by lazy {
        PlayerPersistenceFacade(
            state = state,
            updateState = updateState,
            playbackStateStore = playbackStateStore,
            playbackRestoreCoordinator = playbackRestoreCoordinator,
            currentPlaybackPositionMs = currentPlaybackPositionMs,
            prepareControllerQueue = prepareControllerQueue,
            launchIo = { task -> scope.launch(dispatchers.io) { task() } },
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

    fun savePlaybackState(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackState(positionMs)
    }

    fun clearPlaybackState() {
        persistenceFacade.clearPlaybackState()
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
            val result = persistenceFacade.restorePlaybackState()
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
