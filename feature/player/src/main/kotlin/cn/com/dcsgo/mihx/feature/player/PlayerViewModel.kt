package cn.com.dcsgo.mihx.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import cn.com.dcsgo.mihx.feature.player.runtime.PlayerRuntime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val runtime: PlayerRuntime,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState.empty)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

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
    fun loadLibrary() = runtime.loadLibrary()

    fun onJumpTo(index: Int) = runtime.jumpTo(index)
    fun onRemoveAt(index: Int) = runtime.removeAt(index)
    fun onSwitchMode(mode: PlayMode) = runtime.switchPlayMode(mode)
}
