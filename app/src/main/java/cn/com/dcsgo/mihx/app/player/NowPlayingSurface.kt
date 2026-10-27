package cn.com.dcsgo.mihx.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
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
import cn.com.dcsgo.mihx.ui.components.loadSongInfoSafely

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
    /** 2026-09-30：SHEET 抽屉形态下需要更大的顶 padding 抵消 sheet 顶部 drag handle,
     * 让「状态栏底→封面」视觉对齐默认三页（实测三页≈71dp）。默认 16dp = 三页形态。 */
    topContentPaddingDp: androidx.compose.ui.unit.Dp = 16.dp,
) {
    // 播放位置窄流：**不**在此处订阅 —— 评审 2026-10-27 · M-14。
    // 原先在这里 collect，每 200ms 一个新值经 HomeRouteState 一路传到 31 参数的
    // SongInfoSection → 整棵 Home 子树每秒重组 5 次。现在把窄流本身传下去，
    // 由进度条叶子节点自己订阅 —— 重组范围收缩到那一小块。
    val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
    // 播放页"更多"功能对话框状态
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(songForInfo) {
        val uri = songForInfo?.uri
        if (uri != null) {
            // 评审 2026-10-27 · C-1：统一走 :core:ui 的兜底加载器（其余 7 处调用点同步收敛）。
            // 底层 DB 异常会让协程崩溃，这里兜住；取消异常仍原样上抛。
            songInfo = loadSongInfoSafely(loadSongInfo, songForInfo)
        }
    }
    // 评审 2026-10-27 · M-18：Route 的 state / actions 实例用 `remember` 固定身份。
    //
    // 原先每次重组都新建这两个对象（Actions 里更是全��新 lambda），于是
    // `HomeRoute(state, actions, …)` 的参数每次都变 → 路由**永远无法跳过重组**，
    // Route 边界形同虚设。配合 `@Stable` 标注（feature 层已统一补齐），
    // 路由才真正具备跳过能力。
    val homeState = remember(
        uiState.currentSong,
        uiState.isPlaying,
        uiState.durationMs,
        uiState.playQueue.playMode,
        uiState.isInfinitePlay,
        uiState.isExplorePlay,
        uiState.sameNameSongs,
        uiState.isSleepTimerActive,
        sleepTimerRemainingMs,
        uiState.sleepTimerPlayLastSong,
        uiState.sleepTimerPausePending,
    ) {
        HomeRouteState(
            currentSong = uiState.currentSong,
            isPlaying = uiState.isPlaying,
            positionMs = playerViewModel.positionMs,
            durationMs = uiState.durationMs,
            playMode = uiState.playQueue.playMode,
            isInfinitePlay = uiState.isInfinitePlay,
            isExplorePlay = uiState.isExplorePlay,
            sameNameSongs = uiState.sameNameSongs,
            isSleepTimerActive = uiState.isSleepTimerActive,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            sleepTimerPlayLastSong = uiState.sleepTimerPlayLastSong,
            sleepTimerPausePending = uiState.sleepTimerPausePending,
        )
    }
    val homeActions = remember(
        playerViewModel,
        playlistResumeViewModel,
        showToast,
        onShowQueue,
        onNavigateToLyrics,
        onNavigateToArtist,
        onNavigateToAlbum,
        uiState.currentSong?.id,
    ) {
        HomeRouteActions(
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
                playerViewModel.playRandomQueue { started, _ ->
                    showToast(
                        if (started) "已生成随机队列，开始播放~"
                        else "还没有可播放的音乐，请先导入歌曲吧~"
                    )
                }
                playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
            },
            onStartInfinitePlay = {
                playerViewModel.startInfinitePlay { started, moodSlotName ->
                    if (started) {
                        moodSlotName?.let { slotName ->
                            showToast("已按「$slotName」开启无限随机播放")
                        }
                    } else {
                        showToast("还没有可播放的音乐，请先导入歌曲吧~")
                    }
                }
                playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
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
        )
    }
    HomeRoute(
        topContentPaddingDp = topContentPaddingDp,
        state = homeState,
        actions = homeActions,
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
