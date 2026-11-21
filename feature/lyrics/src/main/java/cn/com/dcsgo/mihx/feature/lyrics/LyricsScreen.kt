package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.ui.lyrics.LyricsView
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LyricsScreen(
    currentSong: Song?,
    lyrics: Lyrics,
    currentPositionMs: StateFlow<Long>,
    isPlaying: Boolean,
    fontScale: Float = 1f,
    onFontScaleChange: (Float) -> Unit = {},
    onBackClick: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    LyricsView(
        songTitle = currentSong?.title.orEmpty(),
        songArtist = currentSong?.artist.orEmpty(),
        lyrics = lyrics,
        positionMs = currentPositionMs,
        isPlaying = isPlaying,
        onBackClick = onBackClick,
        onSeekTo = onSeekTo,
        fontScale = fontScale,
        onFontScaleChange = onFontScaleChange,
    )
}
