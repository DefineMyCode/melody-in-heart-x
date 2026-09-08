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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import cn.com.dcsgo.mihx.feature.player.PlayQueueSheet
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
import cn.com.dcsgo.mihx.ui.components.LocalEmotionCorrectionController
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

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
    moodTimeSlotViewModel: cn.com.dcsgo.mihx.app.mood.MoodTimeSlotViewModel,
    /** 播放全屏抽屉（方案D实验）：由 AppRoot 控制显隐 */
    showPlayerSheet: Boolean = false,
    onPlayerSheetDismiss: () -> Unit = {},
) {
    // 失败歌曲手动标记等 suspend 回调的协程作用域
    val navCoroutineScope = rememberCoroutineScope()

    // 全库分组结果 AppRoot 级共享：曲库/歌单详情/歌手/专辑/榜单/版本管理等路由
    // 原先各自 remember(uiState.songs) 重复执行 O(n) 分组，1100+ 首曲库下切页即重算。
    // 提升到 NavHost 层只算一次，按 songs 列表实例失效。
    val sharedLibrarySongs = remember(uiState.songs) {
        flatGroupedSongs(uiState, playerViewModel)
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.PLAYLIST,
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
            val songSortMode by playerViewModel.songSortMode.collectAsStateWithLifecycle()
            val songSortAscending by playerViewModel.songSortAscending.collectAsStateWithLifecycle()
            PlaylistRoute(
                state = playlistRouteState(
                    uiState,
                    playerViewModel,
                    selectedPlaylist = null,
                    emotionRows = emotionRowsUi.map {
                        EmotionSongUiRow(song = it.song, tags = it.tags, corrected = it.corrected)
                    },
                    precomputedLibrarySongs = sharedLibrarySongs,
                    sortMode = songSortMode,
                    sortAscending = songSortAscending,
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
                    precomputedLibrarySongs = sharedLibrarySongs,
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
            val allSongs = sharedLibrarySongs
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
            val allSongs = sharedLibrarySongs
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
                // C-2（评审 2026-09-03）：底层是 Room runBlocking 桥，DB 异常统一兜底空快照。
                // 性能：先取缓存立即渲染（切页零等待），再后台刷新替换为新值。
                runCatching { playerViewModel.loadPlaybackStatsSnapshot() }
                    .onSuccess { value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "loadPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
                runCatching { playerViewModel.refreshPlaybackStatsSnapshot() }
                    .onSuccess { if (it != value) value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "refreshPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
            }
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
                    dialogVisible = editingSlot != null || showAddDialog,
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
                    onRunValidation = { mode -> playerViewModel.validateLocalFiles(mode) },
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
                runCatching { playerViewModel.refreshPlaybackStatsSnapshot() }
                    .onSuccess { if (it != value) value = it }
                    .onFailure {
                        AppLog.error("AppNavHost", "refreshPlaybackStatsSnapshot failed: ${it.message}", it)
                    }
            }
            val librarySongs = sharedLibrarySongs
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
            val librarySongs = sharedLibrarySongs
            SongTopListRoute(
                state = songTopListRouteState(uiState, playerViewModel, snapshot, period, librarySongs),
                actions = songTopListRouteActions(navController, playerViewModel, playlistResumeViewModel),
            )
        }

        composable(AppRoutes.VERSION_MANAGEMENT) {
            // 全库分组只在曲库变化时重算，避免每次重组都 O(n) 过滤+分组
            val allSongs = sharedLibrarySongs
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
            val allSongs = sharedLibrarySongs
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
            val librarySongs = sharedLibrarySongs
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
            val librarySongs = sharedLibrarySongs
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
            // 校准控制器必须在 composable 上下文读取（CompositionLocal）
            val emotionCorrectionController = LocalEmotionCorrectionController.current
            LaunchedEffect(Unit) {
                emotionViewModel.refresh()
            }
            // 失败歌曲行：songId → 标题/标记状态映射（2026-09-04 失败标记 UI）。
            // calibratedTags 走 suspend 查询（逐首，失败数个位数），produceState 驱动；
            // key = failures 指纹（数量+最近失败时间）+ calibrationVersion——
            // 手动标记不改变失败记录本身（attempts/failedAt 不变），若只看失败指纹
            // 标记后 UI 不会刷新（2026-09-04 回归），故标记成功时递增版本号强制重查。
            var calibrationVersion by remember { mutableStateOf(0) }
            val failuresFingerprint = emotionStatus.failures.entries
                .joinToString("|") { "${it.key}:${it.value.attempts}:${it.value.failedAt}" }
            val failedRows by produceState<List<cn.com.dcsgo.mihx.feature.user.FailedEmotionSong>>(
                initialValue = emptyList(),
                key1 = failuresFingerprint,
                key2 = uiState.songs,
                key3 = calibrationVersion,
            ) {
                value = emotionStatus.failures.mapNotNull { (songId, failure) ->
                    val song = uiState.songs.firstOrNull { it.id == songId }
                        ?: return@mapNotNull null
                    val tags = runCatching { emotionViewModel.calibratedTagsOf(songId) }
                        .getOrDefault(emptyList())
                    cn.com.dcsgo.mihx.feature.user.FailedEmotionSong(
                        songId = songId,
                        title = song.title,
                        reason = failure.reason,
                        attempts = failure.attempts,
                        calibratedTags = tags,
                    )
                }
            }
            // 失败歌曲行 + songId → Song 映射（批量加歌单需要 Song 对象）
            val failedSongMap = emotionStatus.failures.keys
                .mapNotNull { id -> uiState.songs.firstOrNull { it.id == id }?.let { id to it } }
                .toMap()
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
                    failures = failedRows,
                    failedSongMap = failedSongMap,
                    playlists = uiState.playlists,
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
                    onRetryFailed = {
                        emotionViewModel.retryFailedSongs()
                        showToast("已重新排队分析失败歌曲")
                    },
                    onCalibrateSong = { songId, words ->
                        // 全站"不像？标记"控制器（AppRoot CompositionLocal 提供），
                        // 2026-09-04 起失败歌曲(无分析行)标记会创建 user-only 记录
                        navCoroutineScope.launch {
                            val ok = emotionCorrectionController?.save(songId, words) ?: false
                            if (ok) {
                                // 标记不改变失败记录指纹，必须递增版本号触发 failedRows 重查，
                                // 否则列表 UI 不更新、返回页面才可见（2026-09-04 回归）
                                calibrationVersion++
                                emotionViewModel.refresh()
                                showToast("已记录你的标记")
                            } else {
                                showToast("标记失败，请重试")
                            }
                        }
                    },
                    onAddSongsToPlaylist = { songs, playlist ->
                        var added = 0
                        songs.forEach { song ->
                            if (playerViewModel.addSongToPlaylist(playlist.id, song.id)) added++
                        }
                        showToast(
                            if (added > 0) "已将 $added 首歌曲添加到「${playlist.name}」"
                            else "这些歌曲已在「${playlist.name}」中",
                        )
                    },
                    onCreatePlaylistWithResult = playerViewModel::createPlaylist,
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

    // 播放全屏抽屉（方案D实验）：NavHost 之外的模态浮层，承载播放主屏内容
    PlayerSheetHost(
        show = showPlayerSheet,
        onDismiss = onPlayerSheetDismiss,
        navController = navController,
        uiState = uiState,
        playerViewModel = playerViewModel,
        playlistResumeViewModel = playlistResumeViewModel,
        showToast = showToast,
        loadSongInfo = loadSongInfo,
    )
}


/**
 * 播放全屏浮层（方案D）：自绘层，不再用 ModalBottomSheet。
 *
 * - 常驻 composition，show 控制 AnimatedVisibility 挂载（slideIn/OutVertically 从底部进出）
 * - 无独立 dialog window：BACK 走 AppRoot 的 BackHandler，手势/底栏遮挡是普通 Compose 逻辑
 * - 下滑关闭：内容拖拽跟手，松手超过阈值（屏高 1/4）或快速下滑即关
 * - 内容二态：播放主屏（HomeRoute）⇄ 播放队列（PlayQueueSheet 内嵌）
 */
@Composable
private fun PlayerSheetHost(
    show: Boolean,
    onDismiss: () -> Unit,
    navController: NavHostController,
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    playlistResumeViewModel: PlaylistResumeViewModel,
    showToast: (String) -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo?,
) {
    var showQueueInside by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var dragOffsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // 切换主屏/队列时复位拖拽偏移
    androidx.compose.runtime.LaunchedEffect(showQueueInside) { dragOffsetY = 0f }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenHeight = with(density) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val closeThreshold = screenHeight / 4f

    val dragConnection = androidx.compose.runtime.remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            // 内容滚到顶后继续下拉 → 累积为浮层位移（iOS 弹性 sheet 行为）
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource,
            ): androidx.compose.ui.geometry.Offset {
                if (available.y > 0f) {
                    dragOffsetY += available.y
                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override suspend fun onPreFling(
                available: androidx.compose.ui.unit.Velocity,
            ): androidx.compose.ui.unit.Velocity {
                if (dragOffsetY > closeThreshold) onDismiss()
                dragOffsetY = 0f
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    // 普通 composition（无独立 window），BackHandler 直接生效：BACK 分级——先关队列，再关浮层
    androidx.activity.compose.BackHandler(enabled = show && showQueueInside) { showQueueInside = false }

    androidx.compose.animation.AnimatedVisibility(
        visible = show,
        enter = androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.tween(280),
            initialOffsetY = { it },
        ),
        exit = androidx.compose.animation.slideOutVertically(
            animationSpec = androidx.compose.animation.core.tween(240),
            targetOffsetY = { it },
        ),
    ) {
            Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { androidx.compose.ui.unit.IntOffset(0, dragOffsetY.toInt()) }
                .nestedScroll(dragConnection)
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    // 主屏无滚动容器时 nested scroll 链不激活，这里兜底拖拽关闭
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            android.util.Log.w("PlayerOverlay", "drag $dragAmount")
                            change.consume()
                            dragOffsetY = (dragOffsetY + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            android.util.Log.w("PlayerOverlay", "end offset=$dragOffsetY threshold=$closeThreshold")
                            if (dragOffsetY > closeThreshold) onDismiss()
                            dragOffsetY = 0f
                        },
                        onDragCancel = { dragOffsetY = 0f },
                    )
                }
        ) {
            if (showQueueInside) {
                PlayQueueSheet(
                    playQueue = uiState.playQueue,
                    isShown = true,
                    currentSongId = uiState.currentSong?.id,
                    onSongClick = { index ->
                        playerViewModel.playQueueItem(index)
                        showQueueInside = false
                    },
                    onRemoveSong = { index ->
                        playerViewModel.removeFromPlayQueueAt(index)
                        showToast("已从播放队列移除")
                    },
                    onClearQueue = {
                        playerViewModel.clearPlayQueue()
                        showToast("播放队列已清空")
                    },
                    onDismiss = { showQueueInside = false },
                    useInlineShell = true
                )
            } else {
                val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
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
                        onQueueClick = { showQueueInside = true },
                        onTogglePlayMode = {
                            playerViewModel.togglePlayMode()
                            playerViewModel.currentPlayMode.label
                        },
                        onSwitchVersion = playerViewModel::switchToVersion,
                        onShowLyrics = {
                            onDismiss()
                            navController.navigate(AppRoutes.LYRICS)
                        },
                        onArtistClick = { name ->
                            onDismiss()
                            navController.navigate(AppRoutes.artistDetail(name))
                        },
                        onAlbumClick = { name ->
                            onDismiss()
                            navController.navigate(AppRoutes.albumDetail(name))
                        },
                        onLuckyPlayClick = {
                            val started = playerViewModel.playRandomQueue()
                            if (started) {
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
                            if (added > 0) showToast("已关联 $added 首歌曲") else showToast("未检索到关联歌曲")
                        },
                        onSleepTimerStart = { minutes, playLast ->
                            playerViewModel.startSleepTimer(minutes, playLast)
                            showToast("已设置定时关闭：${minutes}分钟后暂停播放")
                        },
                        onSleepTimerCancel = {
                            playerViewModel.cancelSleepTimer()
                            showToast("已取消定时关闭")
                        },
                        onShowSongInfo = { },
                        onAddToPlaylist = { },
                        onDeleteSong = { },
                    ),
                    showToast = showToast,
                )
            }
        }
    }
}
