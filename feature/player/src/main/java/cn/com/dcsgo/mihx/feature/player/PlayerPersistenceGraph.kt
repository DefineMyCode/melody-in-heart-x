package cn.com.dcsgo.mihx.feature.player

import android.os.SystemClock
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinator
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

    fun savePlaybackStateAsync(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackStateAsync(positionMs)
    }

    fun savePlaybackState(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackState(positionMs)
    }

    fun clearPlaybackState() {
        persistenceFacade.clearPlaybackState()
    }

    fun restorePlaybackState() {
        // 状态读取/解码（DataStore + JSON，内部 runBlocking）放 IO，应用结果回主线程，
        // 启动路径不再被阻塞。
        //
        // 关键保护：若服务端 ExoPlayer 正在播放（息屏后台回来、进程未被杀），
        // 不用 DataStore 快照覆盖 live session。Controller connect 的 syncControllerPlaybackState
        // 会把真实位置同步给 UI。
        scope.launch(dispatchers.io) {
            val result = persistenceFacade.restorePlaybackState()
            if (result != null) {
                withContext(dispatchers.main) {
                    if (hasLiveSession()) {
                        log("restorePlaybackState skipped: live session active (pos=${currentPlaybackPositionMs()}ms)")
                    } else {
                        persistenceFacade.applyRestoreResult(result)
                    }
                }
            }
        }
    }

    fun onPlaybackPosition(positionMs: Long) {
        playbackStateAutosaver.onPlaybackPosition(positionMs)
    }
}
