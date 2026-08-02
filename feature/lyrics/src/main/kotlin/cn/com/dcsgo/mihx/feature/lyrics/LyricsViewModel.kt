package cn.com.dcsgo.mihx.feature.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.LyricLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val facade: LyricsFacade,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LyricsUiState())
    val uiState: StateFlow<LyricsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(facade.currentSongIdFlow, facade.positionFlow) { songId, positionMs ->
                Pair(songId, positionMs)
            }.collect { (songId, positionMs) ->
                val prev = _uiState.value
                // Reload lyrics only when the playing song actually changes.
                val lyrics = if (songId != null && songId != prev.songId) {
                    facade.loadLyrics(songId)
                } else {
                    prev.lyrics
                }
                _uiState.value = LyricsUiState(
                    songId = songId,
                    lyrics = lyrics,
                    // Unsynced lyrics (e.g. plain embedded text) have no time axis, so we do not
                    // highlight or auto-scroll to a line.
                    activeIndex = if (lyrics != null && lyrics.lines.any { it.timeMs > 0 }) {
                        lyrics.indexAt(positionMs)
                    } else {
                        -1
                    },
                )
            }
        }
    }

    /** Seek playback to the tapped lyric line (only meaningful for synced lines). */
    fun onLineClick(line: LyricLine) {
        if (line.timeMs > 0) facade.seekTo(line.timeMs)
    }
}
