package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song

@Stable
data class HomeRouteState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val playMode: PlayMode,
    val isInfinitePlay: Boolean,
    val isExploreModeActive: Boolean = false,
    val isExplorePlay: Boolean = false,
    val sameNameSongs: List<Song>,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerPlayLastSong: Boolean = false,
    val sleepTimerPausePending: Boolean = false,
)

data class HomeRouteActions(
    val onPlayPauseClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onStartSeeking: () -> Unit,
    val onEndSeeking: (Long) -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onQueueClick: () -> Unit,
    val onTogglePlayMode: () -> String,
    val onSwitchVersion: (Song) -> Unit,
    val onShowLyrics: () -> Unit,
    val onArtistClick: (String) -> Unit = {},
    val onAlbumClick: (String) -> Unit = {},
    /** M-5：改 () -> Unit，结果（含归因时段名）由调用方在回调里 toast */
    val onLuckyPlayClick: () -> Unit,
    val onStartInfinitePlay: () -> Unit,
    val onStopInfinitePlay: () -> Unit,
    val onRelatedPlayClick: (Song) -> Unit = {},
    val onSleepTimerStart: (Int, Boolean) -> Unit = { _, _ -> },
    val onSleepTimerCancel: () -> Unit = {},
    val onShowSongInfo: (Song) -> Unit = {},
    val onAddToPlaylist: (Song) -> Unit = {},
    val onDeleteSong: (Song) -> Unit = {},
)

@Composable
fun HomeRoute(
    state: HomeRouteState,
    actions: HomeRouteActions,
    showToast: (String) -> Unit,
    /** 透传给 HomeScreen 的顶部 padding。SHEET 抽屉形态传 31.dp 让「状态栏→封面」对齐默认三页（默认 16dp）。 */
    topContentPaddingDp: androidx.compose.ui.unit.Dp = 16.dp,
) {
    // 探索播放（2026-10-20）：探索模式下「无限随机播放」按钮禁用，点击弹对话框引导去设置页切换模式
    var showExploreHintDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    if (showExploreHintDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExploreHintDialog = false },
            title = { androidx.compose.material3.Text("探索播放中") },
            text = {
                androidx.compose.material3.Text(
                    "若想恢复无限随机播放，请到「我的 → 随心播放增强」中关闭探索模式或切换其它模式。",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showExploreHintDialog = false }) {
                    androidx.compose.material3.Text("知道了")
                }
            },
        )
    }

    HomeScreen(
        topContentPaddingDp = topContentPaddingDp,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        currentPositionMs = state.currentPositionMs,
        durationMs = state.durationMs,
        playMode = state.playMode,
        isInfinitePlay = state.isInfinitePlay,
        isExploreModeActive = state.isExploreModeActive,
        sameNameSongs = state.sameNameSongs,
        isSleepTimerActive = state.isSleepTimerActive,
        sleepTimerRemainingMs = state.sleepTimerRemainingMs,
        sleepTimerPlayLastSong = state.sleepTimerPlayLastSong,
        sleepTimerPausePending = state.sleepTimerPausePending,
        onSleepTimerStart = actions.onSleepTimerStart,
        onSleepTimerCancel = actions.onSleepTimerCancel,
        onShowSongInfo = actions.onShowSongInfo,
        onAddToPlaylist = actions.onAddToPlaylist,
        onDeleteSong = actions.onDeleteSong,
        onPlayPauseClick = actions.onPlayPauseClick,
        onPreviousClick = actions.onPreviousClick,
        onNextClick = actions.onNextClick,
        onStartSeeking = actions.onStartSeeking,
        onEndSeeking = actions.onEndSeeking,
        onSeekTo = actions.onSeekTo,
        onQueueClick = actions.onQueueClick,
        onShowLyrics = actions.onShowLyrics,
        onTogglePlayMode = {
            val label = actions.onTogglePlayMode()
            showToast("播放模式: $label")
        },
        onSwitchVersion = { song ->
            actions.onSwitchVersion(song)
            showToast("已切换到 ${song.sampleRateDisplay} 版本")
        },
        onTextCopied = { text -> showToast("已复制: $text") },
        onArtistClick = actions.onArtistClick,
        onAlbumClick = actions.onAlbumClick,
        onLuckyPlayClick = actions.onLuckyPlayClick,
        onInfinitePlayClick = {
            if (state.isExploreModeActive) {
                // 探索播放（2026-10-20）：无限随机播放按钮失效，仅弹提示对话框
                showExploreHintDialog = true
            } else if (state.isInfinitePlay) {
                actions.onStopInfinitePlay()
                showToast("已退出无限随机播放模式")
            } else {
                actions.onStartInfinitePlay()
            }
        },
        onRelatedPlayClick = {
            state.currentSong?.let { song -> actions.onRelatedPlayClick(song) }
        },
    )
}
