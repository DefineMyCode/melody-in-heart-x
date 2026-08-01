package cn.com.dcsgo.mihx.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.feature.player.runtime.PlayerRuntime
import cn.com.dcsgo.mihx.player.PlaybackStateBuffer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val runtime: PlayerRuntime,
    private val playbackStateRepository: PlaybackStateRepository,
    private val playbackStateBuffer: PlaybackStateBuffer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState.empty)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Timestamp of the last persisted snapshot, used to throttle saves to ~[SAVE_INTERVAL_MS]. */
    private var lastSaveAt = 0L

    init {
        viewModelScope.launch {
            runtime.snapshot.collect { snap ->
                val domain = ControllerPlaybackStateSynchronizer.synchronize(snap)
                _uiState.update { ui ->
                    // While dragging, keep the user's local position; else follow the controller.
                    val position = if (ui.isDragging) ui.sliderPositionMs else domain.positionMs
                    ui.copy(
                        playbackState = domain.playbackState,
                        isPlaying = domain.isPlaying,
                        currentMediaId = domain.currentMediaId,
                        positionMs = position,
                        durationMs = domain.durationMs,
                    )
                }
            }
        }
        viewModelScope.launch {
            runtime.queue.collect { q -> _uiState.update { it.copy(queue = q) } }
        }
        viewModelScope.launch {
            runtime.currentQueueIndex.collect { i -> _uiState.update { it.copy(highlightIndex = i) } }
        }
        viewModelScope.launch {
            combine(runtime.queue, runtime.snapshot) { q, s -> q to s }
                .collect { (queue, snap) ->
                    val state = PlaybackStateSnapshot(
                        songIds = queue.playOrderIds,
                        currentIndex = queue.currentIndex,
                        playMode = queue.playMode,
                        positionMs = snap.positionMs,
                        currentMediaId = snap.currentMediaId,
                        savedAt = System.currentTimeMillis(),
                    )
                    playbackStateBuffer.update(state)
                    val now = System.currentTimeMillis()
                    val due = !snap.buffering && (now - lastSaveAt >= SAVE_INTERVAL_MS || !snap.isPlaying)
                    if (due) {
                        lastSaveAt = now
                        playbackStateRepository.saveSnapshot(state)
                    }
                }
        }
        runtime.start()
    }

    fun progressFlow() = runtime.progressFlow()

    fun onPlayPause() {
        if (_uiState.value.isPlaying) runtime.pause() else runtime.play()
    }

    fun onSeekTo(positionMs: Long) = runtime.seekTo(positionMs)

    fun onSeekDrag(positionMs: Long) {
        _uiState.update { ui -> ui.copy(isDragging = true, sliderPositionMs = positionMs) }
    }

    fun onSeekDragEnded() {
        val pos = _uiState.value.sliderPositionMs
        _uiState.update { it.copy(isDragging = false) }
        runtime.seekTo(pos)
    }

    fun onNext() = runtime.seekToNext()
    fun onPrevious() = runtime.seekToPrevious()

    /** Loads the library into the queue; call after the media-read permission is granted. */
    fun loadLibrary() {
        viewModelScope.launch { runtime.loadLibrary() }
    }

    fun onJumpTo(index: Int) = runtime.jumpTo(index)
    fun onRemoveAt(index: Int) = runtime.removeAt(index)
    fun onSwitchMode(mode: PlayMode) = runtime.switchPlayMode(mode)

    companion object {
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
