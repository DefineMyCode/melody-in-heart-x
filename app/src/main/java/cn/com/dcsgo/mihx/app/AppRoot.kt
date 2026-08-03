package cn.com.dcsgo.mihx.app

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import cn.com.dcsgo.mihx.app.permissions.rememberPermissionCoordinator
import cn.com.dcsgo.mihx.app.player.PlayerQueueSheetHost
import cn.com.dcsgo.mihx.app.theme.SettingsViewModel
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.navigation.AppDestinations
import cn.com.dcsgo.mihx.navigation.AppRoutes
import cn.com.dcsgo.mihx.ui.components.AutoDismissToasts
import cn.com.dcsgo.mihx.ui.components.ToastHost
import cn.com.dcsgo.mihx.ui.components.rememberToastHost
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme

@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    mediaMetadataViewModel: AppMediaMetadataViewModel = viewModel(),
) {
    val toastHost = rememberToastHost()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = backStackEntry?.destination?.route
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val themeVariant by settingsViewModel.themeVariant.collectAsState()
    val lyricFontScale by settingsViewModel.lyricFontScale.collectAsState()
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var showQueueSheet by remember { mutableStateOf(false) }
    val uiState by playerViewModel.uiState.collectAsState()

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    LaunchedEffect(activeRoute) {
        if (
            activeRoute == AppRoutes.HOME ||
            activeRoute == AppRoutes.PLAYLIST ||
            activeRoute?.startsWith("${AppRoutes.PLAYLIST}/") == true ||
            activeRoute == AppRoutes.USER ||
            activeRoute?.startsWith("artist/") == true ||
            activeRoute?.startsWith("album/") == true
        ) {
            currentDestination = AppDestinations.fromRoute(activeRoute)
        }
    }

    if (uiState.isLoading) {
        LoadingSplash()
        return
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            toastHost.showToast(message)
            playerViewModel.clearError()
        }
    }

    val permissionCoordinator = rememberPermissionCoordinator(
        onFolderSelected = { uri ->
            playerViewModel.importFolder(uri) { count ->
                toastHost.showToast(
                    if (count > 0) {
                        "✓ 已添加 $count 首歌曲"
                    } else {
                        "未在该文件夹中找到音乐文件"
                    },
                )
            }
        },
        onPermissionDenied = toastHost::showToast,
    )

    fun deleteSongWithToast(songId: Int) {
        when (val result = playerViewModel.deleteSong(songId)) {
            is DeleteSongResult.Success -> toastHost.showToast(result.message)
            is DeleteSongResult.Failure -> toastHost.showToast(result.reason)
        }
    }

    MusicplayerTheme(darkTheme = isDarkTheme, variant = themeVariant) {
        SyncSystemBarsAppearance(isDarkTheme)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppScaffold(
                currentDestination = currentDestination,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                currentPositionMs = uiState.currentPositionMs,
                durationMs = uiState.durationMs,
                onDestinationSelected = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onPlayPauseClick = playerViewModel::togglePlayPause,
                onPreviousClick = playerViewModel::playPrevious,
                onNextClick = playerViewModel::playNext,
                onNavigateToHome = {
                    navController.navigate(AppRoutes.HOME) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            ) {
                AppNavHost(
                    navController = navController,
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    permissionCoordinator = permissionCoordinator,
                    onShowQueue = { showQueueSheet = true },
                    themeMode = themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    themeVariant = themeVariant,
                    onThemeVariantChange = settingsViewModel::setThemeVariant,
                    lyricFontScale = lyricFontScale,
                    onLyricFontScaleChange = settingsViewModel::setLyricFontScale,
                    loadLyrics = mediaMetadataViewModel::lyricsFor,
                    loadSongInfo = mediaMetadataViewModel::songInfo,
                    showToast = toastHost::showToast,
                    deleteSongWithToast = ::deleteSongWithToast,
                )
            }

            PlayerQueueSheetHost(
                playQueue = uiState.playQueue,
                isShown = showQueueSheet,
                currentSongId = uiState.currentSong?.id,
                onSongClick = { index ->
                    playerViewModel.playQueueItem(index)
                    showQueueSheet = false
                },
                onRemoveSong = { index ->
                    playerViewModel.removeFromPlayQueueAt(index)
                    toastHost.showToast("已从播放队列移除")
                },
                onClearQueue = {
                    playerViewModel.clearPlayQueue()
                    toastHost.showToast("播放队列已清空")
                },
                onDismiss = { showQueueSheet = false },
            )

            ToastHost(toastHost = toastHost)
            AutoDismissToasts(toastHost = toastHost, durationMs = 2000L)
        }
    }
}

@Composable
private fun SyncSystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
}

@Composable
private fun LoadingSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "启动中...",
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
