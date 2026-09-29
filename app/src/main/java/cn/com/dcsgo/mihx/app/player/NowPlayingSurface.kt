package cn.com.dcsgo.mihx.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.feature.home.HomeRoute
import cn.com.dcsgo.mihx.feature.home.HomeRouteActions
import cn.com.dcsgo.mihx.feature.home.HomeRouteState
import cn.com.dcsgo.mihx.feature.player.PlayerUiState
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.DeleteSongConfirmDialog
import cn.com.dcsgo.mihx.ui.components.SingleSongAddToPlaylistDialog
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog

/**
 * 播放页内容（"现在播放"界面本体）。
 *
 * **P3 抽取（2026-09-29）**：这段内容原先内联在 `AppNavHost` 的 `composable(AppRoutes.HOME)`
 * 块里。为了让同一份内容既能作为**普通 Tab**渲染（默认骨架），又能装进**全局抽屉**
 * （`playerEntry = SHEET` 的骨架），把它抽成一个独立 composable。
 *
 * **抽取原则：逐字搬运，行为不变。** 三个对话框（歌曲详情/加入歌单/删除确认）随内容一起搬，
 * 因为它们是播放页"更多"菜单的一部分——抽屉形态同样需要它们。
 *
 * 调用方（[cn.com.dcsgo.mihx.app.AppNavHost]）负责决定把它放在哪里渲染。
 */
@Composable
fun NowPlayingSurface(
    playerViewModel: PlayerViewModel,
    uiState: PlayerUiState,
    onShowQueue: () -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
    deleteSongWithToast: (Int) -> Unit,
    playlistResumeViewModel: PlaylistResumeViewModel,
    onNavigateToLyrics: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
) {
    // 播放位置窄流：只在本内容订阅，不驱动整壳重组
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    // M-6（评审 2026-09-03）：定时关闭剩余毫秒窄流——倒计时每秒 tick 只驱动
    // 定时关闭 Chip 局部重组，不写主 UiState 导致整壳重组。
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    // 播放页"更多"功能对话框状态
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(songForInfo) {
        val uri = songForInfo?.uri
        if (uri != null) {
            // m6（评审 2026-09-03）：底层走 Room runBlocking 桥，DB 异常会让协程崩溃，这里兜底。
            songInfo = runCatching { songForInfo?.let { loadSongInfo(it) } }
                .onFailure {
                    AppLog.error("NowPlayingSurface", "loadSongInfo failed: ${it.message}", it)
                }
                .getOrNull()
        }
    }
    HomeRoute(
        state = HomeRouteState(
            currentSong = uiState.currentSong,
            isPlaying = uiState.isPlaying,
            currentPositionMs = positionMs,
            durationMs = uiState.durationMs,
            playMode = uiState.playQueue.playMode,
            isInfinitePlay = uiState.isInfinitePlay,
            sameNameSongs = uiState.sameNameSongs,
            isSleepTimerActive = uiState.isSleepTimerActive,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            sleepTimerPlayLastSong = uiState.sleepTimerPlayLastSong,
            sleepTimerPausePending = uiState.sleepTimerPausePending,
        ),
        actions = HomeRouteActions(
            onPlayPauseClick = playerViewModel::togglePlayPause,
            onPreviousClick = playerViewModel::playPrevious,
            onNextClick = playerViewModel::playNext,
            onStartSeeking = playerViewModel::startSeeking,
            onEndSeeking = playerViewModel::endSeeking,
            onSeekTo = playerViewModel::seekTo,
            onQueueClick = onShowQueue,
            onTogglePlayMode = {
                playerViewModel.togglePlayMode()
                playerViewModel.currentPlayMode.label
            },
            onSwitchVersion = playerViewModel::switchToVersion,
            onShowLyrics = onNavigateToLyrics,
            onArtistClick = onNavigateToArtist,
            onAlbumClick = onNavigateToAlbum,
            onLuckyPlayClick = {
                val started = playerViewModel.playRandomQueue()
                if (started) {
                    // 情境化随心播放归因（§4.5）：让"这首歌为什么被选中"可解释
                    playerViewModel.currentMoodSlotName()?.let { slotName ->
                        showToast("已按「$slotName」为你随机播放")
                    }
                } else {
                    showToast("还没有可播放的音乐，请先导入歌曲吧~")
                }
                playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                started
            },
            onStartInfinitePlay = {
                val started = playerViewModel.startInfinitePlay()
                if (started) {
                    playerViewModel.currentMoodSlotName()?.let { slotName ->
                        showToast("已按「$slotName」开启无限随机播放")
                    }
                }
                playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                started
            },
            onStopInfinitePlay = playerViewModel::stopInfinitePlay,
            onRelatedPlayClick = { song ->
                val added = playerViewModel.playRelatedSongs(song)
                playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                if (added > 0) {
                    showToast("已关联 $added 首歌曲")
                } else {
                    showToast("未检索到关联歌曲")
                }
            },
            onSleepTimerStart = { minutes, playLast ->
                playerViewModel.startSleepTimer(minutes, playLast)
                showToast("已设置定时关闭：${minutes}分钟后暂停播放")
            },
            onSleepTimerCancel = {
                playerViewModel.cancelSleepTimer()
                showToast("已取消定时关闭")
            },
            onShowSongInfo = { song ->
                songForInfo = song
                songInfo = null
            },
            onAddToPlaylist = { song -> songForAddToPlaylist = song },
            onDeleteSong = { song -> songForDelete = song },
        ),
        showToast = showToast,
    )
    // 歌曲详细信息对话框（更多菜单 → 查看歌曲详细信息）
    val infoSong = songForInfo
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = {
                songForInfo = null
                songInfo = null
            },
        )
    }
    // 添加到歌单对话框（更多菜单 → 添加到歌单）
    songForAddToPlaylist?.let { song ->
        SingleSongAddToPlaylistDialog(
            song = song,
            playlists = uiState.playlists,
            onDismiss = { songForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                playerViewModel.addSongToPlaylist(playlist.id, song.id)
                showToast("已添加到歌单「${playlist.name}」")
                songForAddToPlaylist = null
            },
            onCreatePlaylist = playerViewModel::createPlaylist,
        )
    }
    // 删除确认对话框（更多菜单 → 删除，与本地音乐交互一致）
    songForDelete?.let { song ->
        DeleteSongConfirmDialog(
            song = song,
            onDismiss = { songForDelete = null },
            onConfirm = {
                songForDelete = null
                deleteSongWithToast(song.id)
            },
        )
    }
}
