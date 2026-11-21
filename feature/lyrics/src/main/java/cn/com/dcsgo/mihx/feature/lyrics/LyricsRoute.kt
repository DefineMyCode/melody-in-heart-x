package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.flow.StateFlow

@Stable
data class LyricsRouteState(
    val currentSong: Song?,
    /**
     * 播放位置（毫秒）**窄流**。
     *
     * 评审 2026-11-20 · M-11：原先这里放的是 `currentPositionMs: Long`（每 200ms
     * 一个新值），调用方用 `collectAsStateWithLifecycle().value` 读取组合时快照，
     * 让歌词分支在每次 position 发射时整体重组，无法将重组范围收缩到歌词高亮叶子节点。
     * 现在传窄流，由**叶子节点**（LyricsView）自己订阅，重组范围收缩到歌词内容。
     */
    val currentPositionMs: StateFlow<Long>,
    val isPlaying: Boolean,
    val fontScale: Float = 1f,
)

@Stable
data class LyricsRouteActions(
    val onBackClick: () -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onFontScaleChange: (Float) -> Unit = {},
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
        fontScale = state.fontScale,
        onFontScaleChange = actions.onFontScaleChange,
        onBackClick = actions.onBackClick,
        onSeekTo = actions.onSeekTo,
    )
}
