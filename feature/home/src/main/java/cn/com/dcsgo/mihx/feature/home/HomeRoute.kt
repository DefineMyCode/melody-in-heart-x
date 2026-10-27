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
import kotlinx.coroutines.flow.StateFlow

@Stable
data class HomeRouteState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    /**
     * 播放位置（毫秒）**窄流**。
     *
     * 评审 2026-10-27 · M-14：原先这里放的是 `currentPositionMs: Long`（每 200ms
     * 一个新值），于是**整条 Home 子树**（Route → Screen → 31 参数的 SongInfoSection）
     * 每秒重组 5 次 —— 而真正需要位置的只有进度条与两个时间标签。
     * 现在传窄流，由**叶子节点**（进度条）自己订阅，重组范围收缩到那一小块。
     */
    val positionMs: StateFlow<Long>,
    val durationMs: Long,
    val playMode: PlayMode,
    val isInfinitePlay: Boolean,
    val isExplorePlay: Boolean = false,
    val sameNameSongs: List<Song>,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerPlayLastSong: Boolean = false,
    val sleepTimerPausePending: Boolean = false,
)

/**
 * 评审 2026-10-27 · M-18：标 `@Stable`。
 *
 * 全由函数类型构成的数据类会被 Compose 编译器推断为 **unstable**，于是
 * `HomeRoute(state, actions, …)` 永远无法跳过 —— Route 边界形同虚设。
 * 与 `HomeRouteState` 一样标注后，配合调用方 `remember` 出来的稳定实例，
 * 路由才真正具备跳过重组的能力。
 */
@Stable
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
    // 探索播放（2026-10-20）：探索会话进行中时「无限随机播放」按钮禁用，点击弹对话框
    // 引导去设置页切换模式或点歌退出探索（2026-10-21 调整）
    var showExploreHintDialog by androidx.compose.runtime.remember { mutableStateOf(false) }
    if (showExploreHintDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExploreHintDialog = false },
            title = { androidx.compose.material3.Text("探索播放中") },
            text = {
                androidx.compose.material3.Text(
                    "若想恢复无限随机播放，请到「我的 → 随心播放增强」中切换其它模式或通过点击歌曲退出探索播放。",
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
        positionMs = state.positionMs,
        durationMs = state.durationMs,
        playMode = state.playMode,
        isInfinitePlay = state.isInfinitePlay,
        isExplorePlay = state.isExplorePlay,
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
        // 评审 2026-10-27 · M-18：包装出来的 lambda 用 remember 固定身份 ——
        // 否则每次重组都是新 lambda，`HomeScreen` 的参数每次都变 → 整屏重组。
        onTogglePlayMode = remember(actions, showToast) {
            {
                val label = actions.onTogglePlayMode()
                showToast("播放模式: $label")
            }
        },
        onSwitchVersion = remember(actions, showToast) {
            { song ->
                actions.onSwitchVersion(song)
                showToast("已切换到 ${song.sampleRateDisplay} 版本")
            }
        },
        onTextCopied = remember(showToast) {
            { text -> showToast("已复制: $text") }
        },
        onArtistClick = actions.onArtistClick,
        onAlbumClick = actions.onAlbumClick,
        onLuckyPlayClick = actions.onLuckyPlayClick,
        onInfinitePlayClick = remember(actions, state.isExplorePlay, state.isInfinitePlay, showToast) {
            {
                if (state.isExplorePlay) {
                    // 探索播放（2026-10-20）：探索会话中无限随机播放按钮失效，仅弹提示对话框
                    showExploreHintDialog = true
                } else if (state.isInfinitePlay) {
                    actions.onStopInfinitePlay()
                    showToast("已退出无限随机播放模式")
                } else {
                    actions.onStartInfinitePlay()
                }
            }
        },
        onRelatedPlayClick = remember(actions, state.currentSong) {
            {
                state.currentSong?.let { song -> actions.onRelatedPlayClick(song) }
            }
        },
    )
}
