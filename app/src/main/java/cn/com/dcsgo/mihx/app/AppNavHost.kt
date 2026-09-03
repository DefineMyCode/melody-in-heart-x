package cn.com.dcsgo.mihx.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cn.com.dcsgo.mihx.app.permissions.PermissionCoordinator
import cn.com.dcsgo.mihx.app.player.SongPlaybackStrategy
import cn.com.dcsgo.mihx.app.player.playWith
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import cn.com.dcsgo.mihx.feature.home.HomeRoute
import cn.com.dcsgo.mihx.feature.home.HomeRouteActions
import cn.com.dcsgo.mihx.feature.home.HomeRouteState
import cn.com.dcsgo.mihx.feature.home.PlayStatsRoute
import cn.com.dcsgo.mihx.feature.home.QuickSkipSongsRoute
import cn.com.dcsgo.mihx.feature.home.QuickSkipSongsRouteActions
import cn.com.dcsgo.mihx.feature.home.QuickSkipSongsRouteState
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRoute
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteActions
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteState
import cn.com.dcsgo.mihx.feature.player.PlayerUiState
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRoute
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRouteActions
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRouteState
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRoute
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRouteActions
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRouteState
import cn.com.dcsgo.mihx.feature.playlist.DeleteSongConfirmDialog
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRoute
import cn.com.dcsgo.mihx.ui.components.SingleSongAddToPlaylistDialog
import cn.com.dcsgo.mihx.feature.settings.SettingsRoute
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteActions
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteState
import cn.com.dcsgo.mihx.feature.user.FileCheckRoute
import cn.com.dcsgo.mihx.feature.user.FileCheckRouteActions
import cn.com.dcsgo.mihx.feature.user.FileCheckRouteState
import cn.com.dcsgo.mihx.feature.user.EmotionAnalysisActions
import cn.com.dcsgo.mihx.feature.user.EmotionAnalysisRoute
import cn.com.dcsgo.mihx.feature.user.EmotionAnalysisState
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.EmotionSongUiRow
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRoute
import cn.com.dcsgo.mihx.feature.user.SongTopListRoute
import cn.com.dcsgo.mihx.feature.user.UserRoute
import cn.com.dcsgo.mihx.feature.user.MoodTimeSlotRoute
import cn.com.dcsgo.mihx.feature.user.MoodTimeSlotRouteActions
import cn.com.dcsgo.mihx.feature.user.MoodTimeSlotRouteState
import cn.com.dcsgo.mihx.feature.user.MoodSlotEditDialog
import cn.com.dcsgo.mihx.feature.user.VersionComparisonRoute
import cn.com.dcsgo.mihx.feature.user.VersionComparisonRouteActions
import cn.com.dcsgo.mihx.feature.user.VersionComparisonRouteState
import cn.com.dcsgo.mihx.feature.user.VersionManagementRoute
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteActions
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteState
import cn.com.dcsgo.mihx.navigation.AppDestinations
import cn.com.dcsgo.mihx.navigation.AppRoutes
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import androidx.lifecycle.viewmodel.compose.viewModel

/** 路由 → 其所属底部 Tab 的序号（嵌套路由如设置/统计也映射到所属 Tab，同一 Tab 内序号相同则无转场） */
private fun tabOrdinal(route: String?): Int = AppDestinations.fromRoute(route).ordinal

/** 当前时刻的当日分钟数（0–1439），供情境化随心播放入口卡/配置页判定"生效中" */
private fun currentMinuteOfDay(): Int =
    java.util.Calendar.getInstance().let { calendar ->
        calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
    }

@Composable
fun AppNavHost(
    navController: NavHostController,
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    permissionCoordinator: PermissionCoordinator,
    onShowQueue: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    lyricFontScale: Float,
    onLyricFontScaleChange: (Float) -> Unit,
    loadLyrics: suspend (Song) -> Lyrics,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
    deleteSongWithToast: (Int) -> Unit,
    playlistResumeViewModel: PlaylistResumeViewModel,
    emotionViewModel: cn.com.dcsgo.mihx.app.emotion.EmotionViewModel,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
        enterTransition = {
            val from = tabOrdinal(initialState.destination.route)
            val to = tabOrdinal(targetState.destination.route)
            when {
                // 目标 Tab 序号更大（左滑/前进）：新页从右滑入 + 淡入
                to > from ->
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                        fadeIn(tween(300, easing = LinearOutSlowInEasing))
                // 目标 Tab 序号更小（右滑/后退）：新页从左滑入 + 淡入
                to < from ->
                    slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it } +
                        fadeIn(tween(300, easing = LinearOutSlowInEasing))
                else -> EnterTransition.None
            }
        },
        exitTransition = {
            val from = tabOrdinal(initialState.destination.route)
            val to = tabOrdinal(targetState.destination.route)
            when {
                to > from ->
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it } +
                        fadeOut(tween(300, easing = LinearOutSlowInEasing))
                to < from ->
                    slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } +
                        fadeOut(tween(300, easing = LinearOutSlowInEasing))
                else -> ExitTransition.None
            }
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(AppRoutes.HOME) {
            // 播放位置窄流：只在当前目的地（播放页）订阅，不驱动整壳重组
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
                            AppLog.error("AppNavHost", "loadSongInfo failed: ${it.message}", it)
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
                    onShowLyrics = { navController.navigate(AppRoutes.LYRICS) },
                    onArtistClick = { artistName ->
                        navController.navigate(AppRoutes.artistDetail(artistName))
                    },
                    onAlbumClick = { albumName ->
                        navController.navigate(AppRoutes.albumDetail(albumName))
                    },
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

        composable(AppRoutes.LYRICS) {
            // 播放位置窄流：只在歌词页订阅，随位置推进只重组歌词内容
            val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
            LyricsRoute(
                state = LyricsRouteState(
                    currentSong = uiState.currentSong,
                    currentPositionMs = positionMs,
                    isPlaying = uiState.isPlaying,
                    fontScale = lyricFontScale,
                ),
                actions = LyricsRouteActions(
                    onBackClick = navController::navigateUp,
                    onSeekTo = playerViewModel::seekTo,
                    onFontScaleChange = onLyricFontScaleChange,
                ),
                loadLyrics = loadLyrics,
            )
        }

        composable(AppRoutes.PLAYLIST) {
            val actions = playlistRouteActions(
                navController,
                playerViewModel,
                permissionCoordinator,
                deleteSongWithToast,
                playlistResumeViewModel,
            )
            val emotionRowsUi by emotionViewModel.rows.collectAsStateWithLifecycle()
            PlaylistRoute(
                state = playlistRouteState(
                    uiState,
                    playerViewModel,
                    selectedPlaylist = null,
                    emotionRows = emotionRowsUi.map {
                        EmotionSongUiRow(song = it.song, tags = it.tags, corrected = it.corrected)
                    },
                    precomputedLibrarySongs = remember(uiState.songs) {
                        flatGroupedSongs(uiState, playerViewModel)
                    },
                ),
                // 列表页点歌(全曲库范围):非歌单来源,先结算旧歌单
                actions = actions.copy(
                    onSongClick = { song, contextSongs ->
                        actions.onSongClick(song, contextSongs)
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                ),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
            )
        }

        composable(
            route = AppRoutes.PLAYLIST_DETAIL,
            arguments = listOf(navArgument(AppRoutes.PLAYLIST_ID) { type = NavType.IntType }),
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getInt(AppRoutes.PLAYLIST_ID)
            val selectedPlaylist = uiState.playlists.firstOrNull { playlist -> playlist.id == playlistId }
            val resume by playlistResumeViewModel
                .observeResume(playlistId ?: -1)
                .collectAsStateWithLifecycle(initialValue = null)
            // 解析 + 过滤:记录的歌不在歌单里/文件不可播(uri==null)则不显示
            val resumeSong = selectedPlaylist?.let { playlist ->
                resolveResumeSong(resume, uiState.songs, playlist.songIds.toSet())
            }
            val actions = playlistRouteActions(
                navController,
                playerViewModel,
                permissionCoordinator,
                deleteSongWithToast,
                playlistResumeViewModel,
            )
            val emotionRowsUi by emotionViewModel.rows.collectAsStateWithLifecycle()
            PlaylistRoute(
                state = playlistRouteState(
                    uiState,
                    playerViewModel,
                    selectedPlaylist,
                    resumeSong,
                    emotionRows = emotionRowsUi.map {
                        EmotionSongUiRow(song = it.song, tags = it.tags, corrected = it.corrected)
                    },
                    precomputedLibrarySongs = remember(uiState.songs) {
                        flatGroupedSongs(uiState, playerViewModel)
                    },
                ),
                actions = actions.copy(
                    // 歌单内点歌:仅更新来源标记,不立即写记录;记录在退出应用/切换播放源时结算
                    onSongClick = { song, contextSongs ->
                        actions.onSongClick(song, contextSongs)
                        val inPlaylist = selectedPlaylist?.songIds?.contains(song.id) == true
                        playlistResumeViewModel.switchSource(
                            if (inPlaylist) selectedPlaylist.id else null,
                            uiState.currentSong?.id,
                        )
                    },
                    onResumePlaylist = { resumeSongInner, songs ->
                        playerViewModel.playWith(resumeSongInner, SongPlaybackStrategy.scope(songs))
                        playlistResumeViewModel.switchSource(selectedPlaylist?.id, uiState.currentSong?.id)
                        // 继续播放后横幅消失(用户决策)
                        selectedPlaylist?.let { playlistResumeViewModel.clear(it.id) }
                    },
                    onDismissResume = { selectedPlaylist?.let { playlistResumeViewModel.clear(it.id) } },
                    onPlayAllInPlaylist = { playlistSongs ->
                        actions.onPlayAllInPlaylist(playlistSongs)
                        playlistResumeViewModel.switchSource(selectedPlaylist?.id, uiState.currentSong?.id)
                    },
                    onPlayAllFromEndInPlaylist = { playlistSongs ->
                        actions.onPlayAllFromEndInPlaylist(playlistSongs)
                        playlistResumeViewModel.switchSource(selectedPlaylist?.id, uiState.currentSong?.id)
                    },
                ),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
            )
        }

        composable(
            route = AppRoutes.ARTIST_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARTIST_NAME) { type = NavType.StringType }),
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString(AppRoutes.ARTIST_NAME).orEmpty()
            // M-7（评审 2026-09-03）：全库分组只在曲库变化时重算，避免每次重组都 O(n) 分组
            val allSongs = remember(uiState.songs) {
                playerViewModel.getGroupedSongs(uiState.songs).flatten()
            }
            ArtistDetailRoute(
                state = ArtistDetailRouteState(
                    artistName = artistName,
                    songs = allSongs,
                    playlists = uiState.playlists,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                ),
                actions = ArtistDetailRouteActions(
                    onBack = navController::navigateUp,
                    onSongClick = { song, contextSongs ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                    onAlbumClick = { albumName ->
                        navController.navigate(AppRoutes.albumDetail(albumName))
                    },
                    onAddSongToPlaylist = { song, playlist ->
                        playerViewModel.addSongToPlaylist(playlist.id, song.id)
                    },
                    onAddSongsToPlaylist = { songs, playlist ->
                        songs.count { song -> playerViewModel.addSongToPlaylist(playlist.id, song.id) }
                    },
                    onCreatePlaylistWithResult = playerViewModel::createPlaylist,
                ),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
            )
        }

        composable(
            route = AppRoutes.ALBUM_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ALBUM_NAME) { type = NavType.StringType }),
        ) { backStackEntry ->
            val albumName = backStackEntry.arguments?.getString(AppRoutes.ALBUM_NAME).orEmpty()
            // M-7（评审 2026-09-03）：同 ARTIST_DETAIL，分组结果 remember 化
            val allSongs = remember(uiState.songs) {
                playerViewModel.getGroupedSongs(uiState.songs).flatten()
            }
            AlbumDetailRoute(
                state = AlbumDetailRouteState(
                    albumName = albumName,
                    songs = allSongs,
                    playlists = uiState.playlists,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                ),
                actions = AlbumDetailRouteActions(
                    onBack = navController::navigateUp,
                    onSongClick = { song, contextSongs ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                    onAlbumClick = { albumName ->
                        navController.navigate(AppRoutes.albumDetail(albumName))
                    },
                    onAddSongToPlaylist = { song, playlist ->
                        playerViewModel.addSongToPlaylist(playlist.id, song.id)
                    },
                    onAddSongsToPlaylist = { songs, playlist ->
                        songs.count { song -> playerViewModel.addSongToPlaylist(playlist.id, song.id) }
                    },
                    onCreatePlaylistWithResult = playerViewModel::createPlaylist,
                ),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
            )
        }

        composable(AppRoutes.USER) {
            val validationResult by playerViewModel.validationResult.collectAsStateWithLifecycle()
            val isValidating by playerViewModel.isValidating.collectAsStateWithLifecycle()
            val emotionStatus by emotionViewModel.status.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { emotionViewModel.refresh() }
            val snapshot by produceState(PlaybackStatsSnapshot.EMPTY) {
                // C-2（评审 2026-09-03）：底层是 Room runBlocking 桥，DB 异常会在 producer 协程
                // 中未捕获并直接崩溃应用；这里统一兜底为空快照 + 日志。
                runCatching { playerViewModel.loadPlaybackStatsSnapshot() }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
            }
            val moodTimeSlotViewModel: cn.com.dcsgo.mihx.app.mood.MoodTimeSlotViewModel = viewModel()
            val moodConfigs by moodTimeSlotViewModel.configs.collectAsStateWithLifecycle()
            val moodEnabled by moodTimeSlotViewModel.moodTimeSlotEnabled.collectAsStateWithLifecycle()
            // 入口卡"生效中"态的当前时刻：分钟级刷新即可（不必每秒）
            var moodNowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
            LaunchedEffect(Unit) {
                while (true) {
                    moodNowMinute = currentMinuteOfDay()
                    kotlinx.coroutines.delay(30_000L)
                }
            }
            UserRoute(
                state = userRouteState(
                    snapshot = snapshot,
                    validationResult = validationResult,
                    isValidating = isValidating,
                    emotionStatus = emotionStatus,
                    moodSlotConfigs = moodConfigs,
                    moodSlotEnabled = moodEnabled,
                    nowMinuteOfDay = moodNowMinute,
                ),
                actions = userRouteActions(
                    navController = navController,
                    onEmotionScanNow = {
                        emotionViewModel.startManualScan()
                        showToast("已开始扫描，可离开本页，后台继续")
                    },
                    onOpenMoodTimeSlot = { navController.navigate(AppRoutes.MOOD_TIME_SLOT) },
                ),
            )
        }

        composable(AppRoutes.MOOD_TIME_SLOT) {
            val moodTimeSlotViewModel: cn.com.dcsgo.mihx.app.mood.MoodTimeSlotViewModel = viewModel()
            val moodConfigs by moodTimeSlotViewModel.configs.collectAsStateWithLifecycle()
            val moodEnabled by moodTimeSlotViewModel.moodTimeSlotEnabled.collectAsStateWithLifecycle()
            val moodTagCounts by moodTimeSlotViewModel.tagCounts.collectAsStateWithLifecycle()
            val moodLibrarySize by moodTimeSlotViewModel.librarySize.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { moodTimeSlotViewModel.loadStats() }
            var editingSlot by remember { mutableStateOf<cn.com.dcsgo.mihx.core.model.TimeSlotConfig?>(null) }
            var showAddDialog by remember { mutableStateOf(false) }
            var moodNowMinute by remember { mutableStateOf(currentMinuteOfDay()) }
            LaunchedEffect(Unit) {
                while (true) {
                    moodNowMinute = currentMinuteOfDay()
                    kotlinx.coroutines.delay(30_000L)
                }
            }
            MoodTimeSlotRoute(
                state = MoodTimeSlotRouteState(
                    configs = moodConfigs,
                    enabled = moodEnabled,
                    tagCounts = moodTagCounts.associate { it.tag to it.songCount },
                    librarySize = moodLibrarySize,
                    nowMinuteOfDay = moodNowMinute,
                ),
                actions = MoodTimeSlotRouteActions(
                    onBack = navController::navigateUp,
                    onToggleEnabled = { moodTimeSlotViewModel.setEnabled(it) },
                    onEditSlot = { editingSlot = it },
                    onDeleteSlot = { id ->
                        moodTimeSlotViewModel.delete(id)
                        showToast("已删除时段配置")
                    },
                    onAddSlot = { showAddDialog = true },
                ),
                showToast = showToast,
            )
            val editing = editingSlot
            if (editing != null || showAddDialog) {
                MoodSlotEditDialog(
                    editing = editing,
                    existingConfigs = moodConfigs,
                    tagCounts = moodTagCounts.associate { it.tag to it.songCount },
                    librarySize = moodLibrarySize,
                    availableTags = moodTagCounts.map { it.tag },
                    manualOnlyTags = listOf("鬼畜", "沙雕", "戏谑", "荒诞"),
                    onDismiss = {
                        editingSlot = null
                        showAddDialog = false
                    },
                    onSave = { config ->
                        moodTimeSlotViewModel.save(config) { success, message ->
                            if (success) {
                                editingSlot = null
                                showAddDialog = false
                                showToast("已保存时段配置")
                            } else {
                                showToast(message ?: "保存失败")
                            }
                        }
                    },
                )
            }
        }

        composable(AppRoutes.FILE_CHECK) {
            val validationResult by playerViewModel.validationResult.collectAsStateWithLifecycle()
            val isValidating by playerViewModel.isValidating.collectAsStateWithLifecycle()
            FileCheckRoute(
                state = FileCheckRouteState(
                    validationResult = validationResult,
                    isValidating = isValidating,
                ),
                actions = FileCheckRouteActions(
                    onBack = navController::navigateUp,
                    onRunValidation = playerViewModel::validateLocalFiles,
                    onAcknowledge = {
                        playerViewModel.acknowledgeValidationResult()
                        navController.navigateUp()
                    },
                ),
            )
        }

        composable(AppRoutes.PLAYBACK_STATS) {
            val snapshot by produceState(PlaybackStatsSnapshot.EMPTY) {
                runCatching { playerViewModel.loadPlaybackStatsSnapshot() }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
            }
            val librarySongs = remember(uiState.songs) { flatGroupedSongs(uiState, playerViewModel) }
            PlaybackStatsRoute(
                state = playbackStatsRouteState(uiState, playerViewModel, snapshot, librarySongs),
                actions = playbackStatsRouteActions(navController, playerViewModel, playlistResumeViewModel),
            )
        }

        composable(
            route = AppRoutes.SONG_TOP_LIST_FULL,
            arguments = listOf(
                navArgument(AppRoutes.SONG_TOP_PERIOD) {
                    type = NavType.StringType
                    defaultValue = "week"
                },
            ),
        ) { backStackEntry ->
            val period = backStackEntry.arguments?.getString(AppRoutes.SONG_TOP_PERIOD) ?: "week"
            val snapshot by produceState(PlaybackStatsSnapshot.EMPTY) {
                runCatching { playerViewModel.loadPlaybackStatsSnapshot() }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
            }
            val librarySongs = remember(uiState.songs) { flatGroupedSongs(uiState, playerViewModel) }
            SongTopListRoute(
                state = songTopListRouteState(uiState, playerViewModel, snapshot, period, librarySongs),
                actions = songTopListRouteActions(navController, playerViewModel, playlistResumeViewModel),
            )
        }

        composable(AppRoutes.VERSION_MANAGEMENT) {
            // 全库分组只在曲库变化时重算，避免每次重组都 O(n) 过滤+分组
            val allSongs = remember(uiState.songs) {
                playerViewModel.getGroupedSongs(uiState.songs).flatten()
            }
            VersionManagementRoute(
                state = VersionManagementRouteState(
                    songs = allSongs,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                ),
                actions = VersionManagementRouteActions(
                    onBack = navController::navigateUp,
                    // 点击版本：将该歌曲所有版本按列表顺序入队，从点击的版本开始顺序播放（替换并清空原队列）
                    onPlayVersion = { song ->
                        playerViewModel.playWith(
                            song,
                            SongPlaybackStrategy.scope(playerViewModel.getSongsWithSameName(song, allSongs)),
                        )
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                    onAddToQueue = playerViewModel::addToPlayQueue,
                    onDeleteSong = { song -> deleteSongWithToast(song.id) },
                    onDetachVersion = playerViewModel::detachSongFromGroup,
                    onReassignVersion = playerViewModel::reassignSongToGroup,
                    onCompare = { group ->
                        navController.navigate(AppRoutes.versionComparison(group.groupKey))
                    },
                    onCopied = { text -> showToast("已复制: $text") },
                ),
                showToast = showToast,
                loadSongInfo = loadSongInfo,
            )
        }

        composable(
            route = AppRoutes.VERSION_COMPARISON,
            arguments = listOf(navArgument(AppRoutes.VERSION_GROUP_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString(AppRoutes.VERSION_GROUP_ID).orEmpty()
            val allSongs = remember(uiState.songs) {
                playerViewModel.getGroupedSongs(uiState.songs).flatten()
            }
            val groupSongs = remember(groupId, allSongs) {
                allSongs.filter { it.groupKey == groupId }
            }
            // 播放位置窄流：只在对比页订阅，供底部进度条拖拽
            val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
            VersionComparisonRoute(
                state = VersionComparisonRouteState(
                    songs = groupSongs,
                    allSongs = allSongs,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    currentPositionMs = positionMs,
                    durationMs = uiState.durationMs,
                ),
                actions = VersionComparisonRouteActions(
                    onBack = navController::navigateUp,
                    // 播放某版本：该分组所有版本作为上下文队列，从点击的版本开始顺序播放
                    onPlayVersion = { song ->
                        playerViewModel.playWith(
                            song,
                            SongPlaybackStrategy.scope(playerViewModel.getSongsWithSameName(song, allSongs)),
                        )
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                    onSeekTo = playerViewModel::seekTo,
                    onDeleteSong = { song -> deleteSongWithToast(song.id) },
                ),
                showToast = showToast,
                loadSongInfo = loadSongInfo,
            )
        }

        composable(AppRoutes.RAW_PLAY_STATS) {
            val rankedCounts by produceState(emptyList<Pair<Int, Int>>(), true) {
                runCatching { playerViewModel.loadRankedCounts(useRawCounts = true) }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadRankedCounts(raw) failed: ${it.message}", it)
                    }
            }
            val librarySongs = remember(uiState.songs) { flatGroupedSongs(uiState, playerViewModel) }
            PlayStatsRoute(
                state = playStatsRouteState(
                    title = "播放次数统计",
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    rankedCounts = rankedCounts,
                    precomputedLibrarySongs = librarySongs,
                ),
                actions = playStatsRouteActions(navController, playerViewModel, playlistResumeViewModel),
            )
        }

        composable(AppRoutes.EFFECTIVE_PLAY_STATS) {
            val rankedCounts by produceState(emptyList<Pair<Int, Int>>(), false) {
                runCatching { playerViewModel.loadRankedCounts(useRawCounts = false) }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadRankedCounts(effective) failed: ${it.message}", it)
                    }
            }
            val librarySongs = remember(uiState.songs) { flatGroupedSongs(uiState, playerViewModel) }
            PlayStatsRoute(
                state = playStatsRouteState(
                    title = "有效播放统计",
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    rankedCounts = rankedCounts,
                    precomputedLibrarySongs = librarySongs,
                ),
                actions = playStatsRouteActions(navController, playerViewModel, playlistResumeViewModel),
            )
        }

        composable(AppRoutes.QUICK_SKIP_SONGS) {
            QuickSkipSongsRoute(
                state = QuickSkipSongsRouteState(
                    songs = playerViewModel.getQuickSkipSongs(),
                    currentSong = uiState.currentSong,
                ),
                actions = QuickSkipSongsRouteActions(
                    onBack = navController::navigateUp,
                    // 点击秒切歌曲：整个秒切列表作为队列，从被点歌曲开始顺序播放
                    onSongClick = { song ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.scope(playerViewModel.getQuickSkipSongs()))
                        playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                    },
                    onDeleteSong = { song -> deleteSongWithToast(song.id) },
                    onSyncToPlaylist = {
                        playerViewModel.syncQuickSkipSongsToPlaylist()
                        showToast("已同步到秒切歌曲歌单")
                    },
                ),
            )
        }

        composable(AppRoutes.EMOTION_ANALYSIS) {
            val emotionStatus by emotionViewModel.status.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) {
                emotionViewModel.refresh()
            }
            EmotionAnalysisRoute(
                state = EmotionAnalysisState(
                    analyzedCount = emotionStatus.analyzedCount,
                    totalCount = emotionStatus.totalCount,
                    scanning = emotionStatus.scanning,
                    paused = emotionStatus.paused,
                    currentSongTitle = emotionStatus.currentSongTitle,
                    lastSongMs = emotionStatus.lastSongMs,
                    avgSongMs = emotionStatus.avgSongMs,
                    correctedCount = emotionStatus.correctedCount,
                ),
                actions = EmotionAnalysisActions(
                    onBack = navController::navigateUp,
                    onTogglePause = {
                        if (emotionStatus.scanning) {
                            emotionViewModel.pauseScan()
                            showToast("将在当前歌曲分析完后暂停")
                        } else {
                            emotionViewModel.resumeScan()
                            showToast("已继续分析")
                        }
                    },
                ),
            )
        }

        composable(AppRoutes.SETTINGS) {
            SettingsRoute(
                state = SettingsRouteState(
                    themeMode = themeMode,
                    themeVariant = themeVariant,
                    globalUniformRandomEnabled = uiState.globalUniformRandomEnabled,
                    dailyListeningGoalMinutes = uiState.dailyListeningGoalMinutes,
                ),
                actions = SettingsRouteActions(
                    onBack = navController::navigateUp,
                    onThemeModeChange = { mode ->
                        onThemeModeChange(mode)
                        showToast(
                            when (mode) {
                                ThemeMode.SYSTEM -> "已切换为跟随系统主题"
                                ThemeMode.LIGHT -> "已切换为浅色主题"
                                ThemeMode.DARK -> "已切换为深色主题"
                            },
                        )
                    },
                    onThemeVariantChange = { variant ->
                        onThemeVariantChange(variant)
                        showToast(
                            when (variant) {
                                ThemeVariant.MONO -> "已切换为墨色主题"
                                ThemeVariant.VERMILION -> "已切换为朱砂 · 心有乐章主题"
                            },
                        )
                    },
                    onGlobalUniformRandomEnabledChange = { enabled ->
                        playerViewModel.setGlobalUniformRandomEnabled(enabled)
                        showToast(if (enabled) "已开启全局均匀随机" else "已关闭全局均匀随机")
                    },
                    onDailyListeningGoalMinutesChange = { minutes ->
                        playerViewModel.setDailyListeningGoalMinutes(minutes)
                        showToast(
                            if (minutes == 0) "已取消每日听歌时长目标"
                            else "已设置每日听歌时长目标：${minutes}分钟",
                        )
                    },
                    onRequestBluetoothPermission = {
                        permissionCoordinator.requestBluetoothConnectPermission {
                            playerViewModel.initializeBluetoothPlayback()
                            showToast("已开启蓝牙播放监听")
                        }
                    },
                    onRequestNotificationPermission = {
                        permissionCoordinator.requestNotificationPermission {
                            playerViewModel.setPlaybackNotificationEnabled(true)
                            showToast("已开启播放通知控制")
                        }
                    },
                ),
            )
        }
    }
}
