package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
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
    val onLuckyPlayClick: () -> Boolean,
    val onStartInfinitePlay: () -> Boolean,
    val onStopInfinitePlay: () -> Unit,
    val onRelatedPlayClick: (Song) -> Unit = {},
    val onSleepTimerStart: (Int, Boolean) -> Unit = { _, _ -> },
    val onSleepTimerCancel: () -> Unit = {},
)

@Composable
fun HomeRoute(
    state: HomeRouteState,
    actions: HomeRouteActions,
    showToast: (String) -> Unit,
) {
    HomeScreen(
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        currentPositionMs = state.currentPositionMs,
        durationMs = state.durationMs,
        playMode = state.playMode,
        isInfinitePlay = state.isInfinitePlay,
        sameNameSongs = state.sameNameSongs,
        isSleepTimerActive = state.isSleepTimerActive,
        sleepTimerRemainingMs = state.sleepTimerRemainingMs,
        sleepTimerPlayLastSong = state.sleepTimerPlayLastSong,
        sleepTimerPausePending = state.sleepTimerPausePending,
        onSleepTimerStart = actions.onSleepTimerStart,
        onSleepTimerCancel = actions.onSleepTimerCancel,
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
        onLuckyPlayClick = {
            val started = actions.onLuckyPlayClick()
            showToast(
                if (started) "已生成随机队列，开始播放~"
                else "还没有可播放的音乐，请先导入歌曲吧~"
            )
        },
        onInfinitePlayClick = {
            if (state.isInfinitePlay) {
                actions.onStopInfinitePlay()
                showToast("已退出无限随机播放模式")
            } else {
                val started = actions.onStartInfinitePlay()
                showToast(
                    if (started) "已开启无限随机播放模式，随机播放全部歌曲~"
                    else "还没有可播放的音乐，请先导入歌曲吧~"
                )
            }
        },
        onRelatedPlayClick = {
            state.currentSong?.let { song -> actions.onRelatedPlayClick(song) }
        },
    )
}
