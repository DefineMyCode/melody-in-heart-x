package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song

data class HomeRouteState(
    val currentSong: Song?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val playMode: PlayMode,
    val isInfinitePlay: Boolean,
    val sameNameSongs: List<Song>,
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
    val onLuckyPlayClick: () -> Unit,
    val onStartInfinitePlay: () -> Unit,
    val onStopInfinitePlay: () -> Unit,
    val onRelatedPlayClick: (Song) -> Unit = {},
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
            actions.onLuckyPlayClick()
            showToast("已生成随机队列，开始播放~")
        },
        onInfinitePlayClick = {
            if (state.isInfinitePlay) {
                actions.onStopInfinitePlay()
                showToast("已退出无限随机播放模式")
            } else {
                actions.onStartInfinitePlay()
                showToast("已开启无限随机播放模式，随机播放全部歌曲~")
            }
        },
        onRelatedPlayClick = {
            state.currentSong?.let { song -> actions.onRelatedPlayClick(song) }
        },
    )
}
