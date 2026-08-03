package cn.com.dcsgo.mihx.app

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cn.com.dcsgo.mihx.app.permissions.PermissionCoordinator
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
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
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRoute
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteActions
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteState
import cn.com.dcsgo.mihx.feature.settings.SettingsRoute
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteActions
import cn.com.dcsgo.mihx.feature.settings.SettingsRouteState
import cn.com.dcsgo.mihx.feature.user.UserRoute
import cn.com.dcsgo.mihx.feature.user.UserRouteActions
import cn.com.dcsgo.mihx.feature.user.UserRouteState
import cn.com.dcsgo.mihx.feature.user.VersionManagementRoute
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteActions
import cn.com.dcsgo.mihx.feature.user.VersionManagementRouteState
import cn.com.dcsgo.mihx.navigation.AppRoutes

@Composable
fun AppNavHost(
    navController: NavHostController,
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    permissionCoordinator: PermissionCoordinator,
    onShowQueue: () -> Unit,
    darkThemeEnabled: Boolean,
    onDarkThemeEnabledChange: (Boolean) -> Unit,
    loadLyrics: suspend (Song) -> Lyrics,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
    deleteSongWithToast: (Int) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.HOME,
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
                    onLuckyPlayClick = playerViewModel::playRandomQueue,
                    onStartInfinitePlay = playerViewModel::startInfinitePlay,
                    onStopInfinitePlay = playerViewModel::stopInfinitePlay,
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
                ),
                actions = LyricsRouteActions(
                    onBackClick = navController::navigateUp,
                    onSeekTo = playerViewModel::seekTo,
                ),
                loadLyrics = loadLyrics,
            )
        }

        composable(AppRoutes.PLAYLIST) {
            PlaylistRoute(
                state = playlistRouteState(uiState, playerViewModel, selectedPlaylist = null),
                actions = playlistRouteActions(navController, playerViewModel),
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
                actions = playlistRouteActions(navController, playerViewModel),
                showToast = showToast,
            )
        }

        composable(AppRoutes.USER) {
            UserRoute(
                state = userRouteState(uiState, playerViewModel),
                actions = userRouteActions(
                    playerViewModel = playerViewModel,
                    permissionCoordinator = permissionCoordinator,
                    onShowVersionManagement = { navController.navigate(AppRoutes.VERSION_MANAGEMENT) },
                    onShowQuickSkipSongs = { navController.navigate(AppRoutes.QUICK_SKIP_SONGS) },
                    onShowSettings = { navController.navigate(AppRoutes.SETTINGS) },
                    onShowRawPlayStats = { navController.navigate(AppRoutes.RAW_PLAY_STATS) },
                    onShowEffectivePlayStats = { navController.navigate(AppRoutes.EFFECTIVE_PLAY_STATS) },
                    deleteSongWithToast = deleteSongWithToast,
                ),
                loadSongInfo = loadSongInfo,
                showToast = showToast,
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
                    onPlayVersion = { song -> playerViewModel.playSongFromContext(song, allSongs) },
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
                    onSongClick = { song ->
                        val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
                        playerViewModel.playSongFromContext(song, allSongs)
                        navController.navigateUp()
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
                    darkThemeEnabled = darkThemeEnabled,
                    globalUniformRandomEnabled = uiState.globalUniformRandomEnabled,
                    bluetoothPlaybackMonitoringEnabled = uiState.bluetoothPlaybackMonitoringEnabled,
                    playbackNotificationEnabled = uiState.playbackNotificationEnabled,
                ),
                actions = SettingsRouteActions(
                    onBack = navController::navigateUp,
                    onDarkThemeEnabledChange = { enabled ->
                        onDarkThemeEnabledChange(enabled)
                        showToast(if (enabled) "已开启深色主题" else "已关闭深色主题")
                    },
                    onGlobalUniformRandomEnabledChange = { enabled ->
                        playerViewModel.setGlobalUniformRandomEnabled(enabled)
                        showToast(if (enabled) "已开启全局均匀随机" else "已关闭全局均匀随机")
                    },
                    onBluetoothPlaybackMonitoringEnabledChange = { enabled ->
                        if (enabled) {
                            permissionCoordinator.requestBluetoothConnectPermission {
                                playerViewModel.initializeBluetoothPlayback()
                                showToast("已开启蓝牙播放监听")
                            }
                        } else {
                            playerViewModel.releaseBluetoothPlayback()
                            showToast("已关闭蓝牙播放监听")
                        }
                    },
                    onPlaybackNotificationEnabledChange = { enabled ->
                        if (enabled) {
                            permissionCoordinator.requestNotificationPermission {
                                playerViewModel.setPlaybackNotificationEnabled(true)
                                showToast("已开启播放通知控制")
                            }
                        } else {
                            playerViewModel.setPlaybackNotificationEnabled(false)
                            showToast("已关闭播放通知控制")
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
    selectedPlaylist = selectedPlaylist,
    selectedPlaylistSongs = selectedPlaylist?.let { playlist ->
        playerViewModel.getGroupedSongs(
            playerViewModel.getSongsByPlaylist(playlist),
        ).flatten()
    },
    currentSong = uiState.currentSong,
    isPlaying = uiState.isPlaying,
)

private fun playlistRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
): PlaylistRouteActions = PlaylistRouteActions(
    onPlaylistClick = { playlist -> navController.navigate(AppRoutes.playlistDetail(playlist.id)) },
    onSongClick = playerViewModel::playSongFromContext,
    onBackClick = navController::navigateUp,
    onCreatePlaylist = playerViewModel::createPlaylist,
    onDeletePlaylist = { playlist -> playerViewModel.deletePlaylist(playlist.id) },
    onRenamePlaylist = { playlist, newName -> playerViewModel.renamePlaylist(playlist.id, newName) },
    onAddSongToPlaylist = { song, playlist -> playerViewModel.addSongToPlaylist(playlist.id, song.id) },
    onRemoveSongFromPlaylist = { song, playlist -> playerViewModel.removeSongFromPlaylist(playlist.id, song.id) },
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

private fun userRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
): UserRouteState = UserRouteState(
    songs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
    playlists = uiState.playlists,
    currentSong = uiState.currentSong,
    isPlaying = uiState.isPlaying,
    isImporting = uiState.isImporting,
    importProgress = uiState.importProgress,
    importTotal = uiState.importTotal,
)

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
    onSongClick = { song ->
        val allSongs = playerViewModel.getGroupedSongs(playerViewModel.uiState.value.songs).flatten()
        playerViewModel.playSongFromContext(song, allSongs)
        navController.navigateUp()
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
    playerViewModel: PlayerViewModel,
    permissionCoordinator: PermissionCoordinator,
    onShowVersionManagement: () -> Unit,
    onShowQuickSkipSongs: () -> Unit,
    onShowSettings: () -> Unit,
    onShowRawPlayStats: () -> Unit,
    onShowEffectivePlayStats: () -> Unit,
    deleteSongWithToast: (Int) -> Unit,
): UserRouteActions = UserRouteActions(
    onAddFolderClick = permissionCoordinator::requestAudioFolderAccess,
    onSongClick = playerViewModel::playSongFromContext,
    onAddSongsToPlaylist = { songs, playlist ->
        songs.count { song -> playerViewModel.addSongToPlaylist(playlist.id, song.id) }
    },
    onDeleteSong = { song -> deleteSongWithToast(song.id) },
    onCreatePlaylist = playerViewModel::createPlaylist,
    onShowVersionManagement = onShowVersionManagement,
    onShowQuickSkipSongs = onShowQuickSkipSongs,
    onShowSettings = onShowSettings,
    onShowRawPlayStats = onShowRawPlayStats,
    onShowEffectivePlayStats = onShowEffectivePlayStats,
)
