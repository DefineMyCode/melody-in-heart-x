package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song

data class LyricsRouteState(
    val currentSong: Song?,
    val currentPositionMs: Long,
    val isPlaying: Boolean,
)

data class LyricsRouteActions(
    val onBackClick: () -> Unit,
    val onSeekTo: (Long) -> Unit,
)

@Composable
fun LyricsRoute(
    state: LyricsRouteState,
    actions: LyricsRouteActions,
    loadLyrics: suspend (Song) -> Lyrics,
) {
    var lyrics by remember(state.currentSong?.uri) { mutableStateOf(Lyrics.EMPTY) }

    LaunchedEffect(state.currentSong?.uri) {
        lyrics = state.currentSong?.let { song -> loadLyrics(song) } ?: Lyrics.EMPTY
    }

    LyricsScreen(
        currentSong = state.currentSong,
        lyrics = lyrics,
        currentPositionMs = state.currentPositionMs,
        isPlaying = state.isPlaying,
        onBackClick = actions.onBackClick,
        onSeekTo = actions.onSeekTo,
    )
}
