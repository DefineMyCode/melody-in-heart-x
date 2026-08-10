package cn.com.dcsgo.mihx.app

import androidx.navigation.NavHostController
import cn.com.dcsgo.mihx.app.permissions.PermissionCoordinator
import cn.com.dcsgo.mihx.app.player.SongPlaybackStrategy
import cn.com.dcsgo.mihx.app.player.playWith
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.feature.home.PlayStatsRouteActions
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteActions
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRouteActions
import cn.com.dcsgo.mihx.feature.user.SongTopListRouteActions
import cn.com.dcsgo.mihx.feature.user.UserRouteActions
import cn.com.dcsgo.mihx.navigation.AppRoutes

/**
 * 路由 Actions 装配
 *
 * 集中各 feature Route 的导航与播放动作绑定，使 [AppNavHost] 只保留路由表与转场。
 */

internal fun playlistRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    permissionCoordinator: PermissionCoordinator,
    deleteSongWithToast: (Int) -> Unit,
    playlistResumeViewModel: PlaylistResumeViewModel,
): PlaylistRouteActions = PlaylistRouteActions(
    onPlaylistClick = { playlist -> navController.navigate(AppRoutes.playlistDetail(playlist.id)) },
    // 点击歌单中的歌曲：将整个歌单按列表顺序入队，从点击的歌曲开始顺序播放（替换并清空原队列）
    onSongClick = { song, contextSongs ->
        playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
    },
    // 点击本地音乐中的歌曲：清空队列，只播放这一首
    onLocalSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
        playlistResumeViewModel.switchSource(null, playerViewModel.uiState.value.currentSong?.id)
    },
    onArtistClick = { artistName -> navController.navigate(AppRoutes.artistDetail(artistName)) },
    onAlbumClick = { albumName ->
        navController.navigate(AppRoutes.albumDetail(albumName))
    },
    onBackClick = navController::navigateUp,
    onCreatePlaylist = playerViewModel::createPlaylist,
    onDeletePlaylist = { playlist ->
        playerViewModel.deletePlaylist(playlist.id)
        playlistResumeViewModel.clear(playlist.id)
    },
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

internal fun userRouteActions(
    navController: NavHostController,
): UserRouteActions = UserRouteActions(
    onShowSettings = { navController.navigate(AppRoutes.SETTINGS) },
    onShowPlaybackStats = { navController.navigate(AppRoutes.PLAYBACK_STATS) },
    onOpenFileCheck = { navController.navigate(AppRoutes.FILE_CHECK) },
)

internal fun playStatsRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    playlistResumeViewModel: PlaylistResumeViewModel,
): PlayStatsRouteActions = PlayStatsRouteActions(
    onBack = navController::navigateUp,
    // 点击统计页歌曲：队列只包含被点击的这一首，不返回上一页
    onSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
        playlistResumeViewModel.switchSource(null, playerViewModel.uiState.value.currentSong?.id)
        // 点击歌曲后停留在当前页，不返回上一页
    },
)

internal fun playbackStatsRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    playlistResumeViewModel: PlaylistResumeViewModel,
): PlaybackStatsRouteActions = PlaybackStatsRouteActions(
    onBack = navController::navigateUp,
    // 点击统计总览预览歌曲：队列只包含被点击的这一首，不返回上一页
    onSongClick = { song ->
        playerViewModel.playWith(song, SongPlaybackStrategy.single())
        playlistResumeViewModel.switchSource(null, playerViewModel.uiState.value.currentSong?.id)
        // 点击歌曲后停留在当前页，不返回上一页
    },
    onOpenPlayCounts = { navController.navigate(AppRoutes.RAW_PLAY_STATS) },
    onOpenEffectivePlayCounts = { navController.navigate(AppRoutes.EFFECTIVE_PLAY_STATS) },
    onOpenWeeklyTop = { navController.navigate(AppRoutes.songTopList("week")) },
    onOpenMonthlyTop = { navController.navigate(AppRoutes.songTopList("month")) },
)

internal fun songTopListRouteActions(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    playlistResumeViewModel: PlaylistResumeViewModel,
): SongTopListRouteActions = SongTopListRouteActions(
    onBack = navController::navigateUp,
    // 点击TOP榜歌曲：把当前时间段（周/月）的整个榜单入队，从被点击歌曲开始播放，不返回上一页
    onSongClick = { song, topSongs ->
        playerViewModel.playWith(song, SongPlaybackStrategy.scope(topSongs))
        playlistResumeViewModel.switchSource(null, playerViewModel.uiState.value.currentSong?.id)
        // 点击歌曲后停留在当前页，不返回上一页
    },
)
