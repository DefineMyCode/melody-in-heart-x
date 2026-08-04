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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cn.com.dcsgo.mihx.app.permissions.PermissionCoordinator
import cn.com.dcsgo.mihx.app.player.SongPlaybackStrategy
import cn.com.dcsgo.mihx.app.player.playWith
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.feature.home.HomeRoute
import cn.com.dcsgo.mihx.feature.home.HomeRouteActions
import cn.com.dcsgo.mihx.feature.home.HomeRouteState
import cn.com.dcsgo.mihx.feature.home.PlayStatsRoute
import cn.com.dcsgo.mihx.feature.home.PlayStatsRouteActions
import cn.com.dcsgo.mihx.feature.home.PlayStatsRouteState
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
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRoute
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteActions
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteState
import cn.com.dcsgo.mihx.feature.settings.SettingsRoute
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteActions
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteState
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRoute
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRouteActions
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRouteState
import cn.com.dcsgo.mihx.feature.user.SongTopListRoute
import cn.com.dcsgo.mihx.feature.user.SongTopListRouteActions
import cn.com.dcsgo.mihx.feature.user.SongTopListRouteState
import cn.com.dcsgo.mihx.feature.user.UserRoute
import cn.com.dcsgo.mihx.feature.user.UserRouteActions
import cn.com.dcsgo.mihx.feature.user.UserRouteState
import cn.com.dcsgo.mihx.feature.user.VersionManagementRoute
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteActions
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteState
import cn.com.dcsgo.mihx.navigation.AppDestinations
import cn.com.dcsgo.mihx.navigation.AppRoutes

/** 路由 → 其所属底部 Tab 的序号（嵌套路由如设置/统计也映射到所属 Tab，同一 Tab 内序号相同则无转场） */
private fun tabOrdinal(route: String?): Int = AppDestinations.fromRoute(route).ordinal

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
            HomeRoute(
                state = HomeRouteState(
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                    currentPositionMs = uiState.currentPositionMs,
                    durationMs = uiState.durationMs,
                    playMode = uiState.playQueue.playMode,
                    isInfinitePlay = uiState.isInfinitePlay,
                    sameNameSongs = uiState.sameNameSongs,
                    isSleepTimerActive = uiState.isSleepTimerActive,
                    sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
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
                    onLuckyPlayClick = playerViewModel::playRandomQueue,
                    onStartInfinitePlay = playerViewModel::startInfinitePlay,
                    onStopInfinitePlay = playerViewModel::stopInfinitePlay,
                    onRelatedPlayClick = { song ->
                        val added = playerViewModel.playRelatedSongs(song)
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
                ),
                showToast = showToast,
            )
        }

        composable(AppRoutes.LYRICS) {
            LyricsRoute(
                state = LyricsRouteState(
                    currentSong = uiState.currentSong,
                    currentPositionMs = uiState.currentPositionMs,
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
            PlaylistRoute(
                state = playlistRouteState(uiState, playerViewModel, selectedPlaylist = null),
                actions = playlistRouteActions(navController, playerViewModel, permissionCoordinator, deleteSongWithToast),
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
            PlaylistRoute(
                state = playlistRouteState(uiState, playerViewModel, selectedPlaylist),
                actions = playlistRouteActions(navController, playerViewModel, permissionCoordinator, deleteSongWithToast),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
            )
        }

        composable(
            route = AppRoutes.ARTIST_DETAIL,
            arguments = listOf(navArgument(AppRoutes.ARTIST_NAME) { type = NavType.StringType }),
        ) { backStackEntry ->
            val artistName = backStackEntry.arguments?.getString(AppRoutes.ARTIST_NAME).orEmpty()
            ArtistDetailRoute(
                state = ArtistDetailRouteState(
                    artistName = artistName,
                    songs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
                    playlists = uiState.playlists,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                ),
                actions = ArtistDetailRouteActions(
                    onBack = navController::navigateUp,
                    onSongClick = { song, contextSongs ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
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
            AlbumDetailRoute(
                state = AlbumDetailRouteState(
                    albumName = albumName,
                    songs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
                    playlists = uiState.playlists,
                    currentSong = uiState.currentSong,
                    isPlaying = uiState.isPlaying,
                ),
                actions = AlbumDetailRouteActions(
                    onBack = navController::navigateUp,
                    onSongClick = { song, contextSongs ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
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
            UserRoute(
                state = userRouteState(playerViewModel),
                actions = userRouteActions(navController),
            )
        }

        composable(AppRoutes.PLAYBACK_STATS) {
            PlaybackStatsRoute(
                state = playbackStatsRouteState(uiState, playerViewModel),
                actions = playbackStatsRouteActions(navController, playerViewModel),
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
            SongTopListRoute(
                state = songTopListRouteState(uiState, playerViewModel, period),
                actions = songTopListRouteActions(navController, playerViewModel),
            )
        }

        composable(AppRoutes.VERSION_MANAGEMENT) {
            val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
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
                    },
                    onAddToQueue = playerViewModel::addToPlayQueue,
                    onDeleteSong = { song -> deleteSongWithToast(song.id) },
                    onDetachVersion = playerViewModel::detachSongFromGroup,
                    onReassignVersion = playerViewModel::reassignSongToGroup,
                    onCopied = { text -> showToast("已复制: $text") },
                ),
                showToast = showToast,
            )
        }

        composable(AppRoutes.RAW_PLAY_STATS) {
            PlayStatsRoute(
                state = playStatsRouteState(
                    title = "播放次数统计",
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    useRawCounts = true,
                ),
                actions = playStatsRouteActions(navController, playerViewModel),
            )
        }

        composable(AppRoutes.EFFECTIVE_PLAY_STATS) {
            PlayStatsRoute(
                state = playStatsRouteState(
                    title = "有效播放统计",
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    useRawCounts = false,
                ),
                actions = playStatsRouteActions(navController, playerViewModel),
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
                    // 点击秒切歌曲：清空队列，只播放这一首，停留在秒切歌曲页面
                    onSongClick = { song ->
                        playerViewModel.playWith(song, SongPlaybackStrategy.single())
                    },
                    onDeleteSong = { song -> deleteSongWithToast(song.id) },
                    onSyncToPlaylist = {
                        playerViewModel.syncQuickSkipSongsToPlaylist()
                        showToast("已同步到秒切歌曲歌单")
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

private fun playlistRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    selectedPlaylist: Playlist?,
): PlaylistRouteState = PlaylistRouteState(
    playlists = uiState.playlists,
    librarySongs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
    libraryArtists = uiState.libraryArtists,
    libraryAlbums = uiState.libraryAlbums,
    selectedPlaylist = selectedPlaylist,
    selectedPlaylistSongs = selectedPlaylist?.let { playlist ->
        playerViewModel.getGroupedSongs(
            playerViewModel.getSongsByPlaylist(playlist),
        ).flatten()
    },
    currentSong = uiState.currentSong,
    isPlaying = uiState.isPlaying,
    isImporting = uiState.isImporting,
    importProgress = uiState.importProgress,
    importTotal = uiState.importTotal,
)

private fun playlistRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    permissionCoordinator: PermissionCoordinator,
    deleteSongWithToast: (Int) -> Unit,
): PlaylistRouteActions = PlaylistRouteActions(
    onPlaylistClick = { playlist -> navController.navigate(AppRoutes.playlistDetail(playlist.id)) },
    // 点击歌单中的歌曲：将整个歌单按列表顺序入队，从点击的歌曲开始顺序播放（替换并清空原队列）
    onSongClick = { song, contextSongs ->
        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
    },
    // 点击本地音乐中的歌曲：清空队列，只播放这一首
    onLocalSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
    },
    onArtistClick = { artistName -> navController.navigate(AppRoutes.artistDetail(artistName)) },
    onAlbumClick = { albumName ->
        navController.navigate(AppRoutes.albumDetail(albumName))
    },
    onBackClick = navController::navigateUp,
    onCreatePlaylist = playerViewModel::createPlaylist,
    onDeletePlaylist = { playlist -> playerViewModel.deletePlaylist(playlist.id) },
    onRenamePlaylist = { playlist, newName -> playerViewModel.renamePlaylist(playlist.id, newName) },
    onAddSongToPlaylist = { song, playlist -> playerViewModel.addSongToPlaylist(playlist.id, song.id) },
    onRemoveSongFromPlaylist = { song, playlist -> playerViewModel.removeSongFromPlaylist(playlist.id, song.id) },
    onReorderPlaylist = playerViewModel::reorderPlaylist,
    onAddFolderClick = permissionCoordinator::requestAudioFolderAccess,
    onAddSongsToPlaylist = { songs, playlist ->
        songs.count { song -> playerViewModel.addSongToPlaylist(playlist.id, song.id) }
    },
    onDeleteSong = { song -> deleteSongWithToast(song.id) },
    onCreatePlaylistWithResult = playerViewModel::createPlaylist,
    onShowVersionManagement = { navController.navigate(AppRoutes.VERSION_MANAGEMENT) },
    onShowQuickSkipSongs = { navController.navigate(AppRoutes.QUICK_SKIP_SONGS) },
    onPlayAllInPlaylist = { playlistSongs ->
        playerViewModel.setPlayQueue(
            playlistSongs,
            startIndex = 0,
            mode = PlayMode.SEQUENTIAL,
        )
    },
    onPlayAllFromEndInPlaylist = { playlistSongs ->
        playerViewModel.setPlayQueue(
            playlistSongs,
            startIndex = playlistSongs.size - 1,
            mode = PlayMode.REVERSE,
        )
    },
    onAddAllToQueueInPlaylist = playerViewModel::addToPlayQueue,
    onAddAllToNextPlayInPlaylist = playerViewModel::addSongsToNextPlay,
    onAddSongToQueue = playerViewModel::addToPlayQueue,
    onAddSongToNextPlay = playerViewModel::addSongToNextPlay,
)

private fun userRouteState(playerViewModel: PlayerViewModel): UserRouteState {
    val snapshot = playerViewModel.playStatsRepository.playbackStatsSnapshot()
    return UserRouteState(
        todayDurationMs = snapshot.todayDurationMs,
        weekTotalMs = snapshot.weekTotalMs,
    )
}

private data class RankedStatsContent(
    val songs: List<Song>,
    val playCounts: Map<Int, Int>,
)

private fun playStatsRouteState(
    title: String,
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    useRawCounts: Boolean,
): PlayStatsRouteState {
    val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
    val statsContent = rankedStatsContent(
        songs = allSongs,
        rankedCounts = playerViewModel.playStatsRepository.getRankedCounts(useRawCounts = useRawCounts),
    )
    return PlayStatsRouteState(
        title = title,
        songs = statsContent.songs,
        playCounts = statsContent.playCounts,
        currentSong = uiState.currentSong,
    )
}

private fun playStatsRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
): PlayStatsRouteActions = PlayStatsRouteActions(
    onBack = navController::navigateUp,
    // 点击统计页歌曲：队列只包含被点击的这一首，不返回上一页
    onSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
        // 点击歌曲后停留在当前页，不返回上一页
    },
)

private fun rankedStatsContent(
    songs: List<Song>,
    rankedCounts: List<Pair<Int, Int>>,
): RankedStatsContent {
    val rankedSongIds = rankedCounts.mapTo(mutableSetOf()) { it.first }
    val songsById = songs.associateBy { it.id }
    val rankedSongs = rankedCounts.mapNotNull { (songId, _) -> songsById[songId] }
    val unplayedSongs = songs.filterNot { it.id in rankedSongIds }

    return RankedStatsContent(
        songs = rankedSongs + unplayedSongs,
        playCounts = rankedCounts.toMap(),
    )
}

private fun userRouteActions(
    navController: NavHostController,
): UserRouteActions = UserRouteActions(
    onShowSettings = { navController.navigate(AppRoutes.SETTINGS) },
    onShowPlaybackStats = { navController.navigate(AppRoutes.PLAYBACK_STATS) },
)

private fun playbackStatsRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
): PlaybackStatsRouteState {
    val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
    return PlaybackStatsRouteState(
        snapshot = playerViewModel.playStatsRepository.playbackStatsSnapshot(),
        songs = allSongs,
        currentSong = uiState.currentSong,
        isPlaying = uiState.isPlaying,
        dailyListeningGoalMinutes = uiState.dailyListeningGoalMinutes,
    )
}

private fun playbackStatsRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
): PlaybackStatsRouteActions = PlaybackStatsRouteActions(
    onBack = navController::navigateUp,
    // 点击统计总览预览歌曲：队列只包含被点击的这一首，不返回上一页
    onSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
        // 点击歌曲后停留在当前页，不返回上一页
    },
    onOpenPlayCounts = { navController.navigate(AppRoutes.RAW_PLAY_STATS) },
    onOpenEffectivePlayCounts = { navController.navigate(AppRoutes.EFFECTIVE_PLAY_STATS) },
    onOpenWeeklyTop = { navController.navigate(AppRoutes.songTopList("week")) },
    onOpenMonthlyTop = { navController.navigate(AppRoutes.songTopList("month")) },
)

private fun songTopListRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    initialPeriod: String = "week",
): SongTopListRouteState {
    val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
    val snapshot = playerViewModel.playStatsRepository.playbackStatsSnapshot()
    return SongTopListRouteState(
        weeklyTop = snapshot.weeklyTop,
        monthlyTop = snapshot.monthlyTop,
        songs = allSongs,
        currentSong = uiState.currentSong,
        initialPeriod = initialPeriod,
    )
}

private fun songTopListRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
): SongTopListRouteActions = SongTopListRouteActions(
    onBack = navController::navigateUp,
    // 点击TOP榜歌曲：把当前时间段（周/月）的整个榜单入队，从被点击歌曲开始播放，不返回上一页
    onSongClick = { song, topSongs ->
        playerViewModel.playWith(song, SongPlaybackStrategy.scope(topSongs))
        // 点击歌曲后停留在当前页，不返回上一页
    },
)
