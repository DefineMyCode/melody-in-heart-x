package cn.com.dcsgo.mihx.feature.player

import android.os.SystemClock
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.playback.PlaybackRestoreCoordinator
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PlayerPersistenceGraph(
    playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val scope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val syncPlaybackState: () -> Unit,
    private val currentPlaybackPositionMs: () -> Long,
    private val prepareControllerQueue: (PlayQueue, Int, Long) -> Boolean,
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
        savePlaybackState = { positionMs -> savePlaybackState(positionMs) },
    )

    fun savePlaybackStateAsync() {
        persistenceFacade.savePlaybackStateAsync()
    }

    fun savePlaybackState(positionMs: Long = currentPlaybackPositionMs()) {
        persistenceFacade.savePlaybackState(positionMs)
    }

    fun clearPlaybackState() {
        persistenceFacade.clearPlaybackState()
    }

    fun restorePlaybackState() {
        persistenceFacade.restorePlaybackState()
    }

    fun onPlaybackPosition(positionMs: Long) {
        playbackStateAutosaver.onPlaybackPosition(positionMs)
    }
}
