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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.view.WindowCompat
import cn.com.dcsgo.mihx.app.permissions.rememberPermissionCoordinator
import cn.com.dcsgo.mihx.app.player.PlayerQueueSheetHost
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.app.theme.SettingsViewModel
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.navigation.AppDestinations
import cn.com.dcsgo.mihx.navigation.AppRoutes
import cn.com.dcsgo.mihx.ui.components.AutoDismissToasts
import cn.com.dcsgo.mihx.ui.components.EmotionCorrectionController
import cn.com.dcsgo.mihx.ui.components.LocalEmotionCorrectionController
import cn.com.dcsgo.mihx.ui.components.ToastHost
import cn.com.dcsgo.mihx.ui.components.rememberToastHost
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun AppRoot(
    playerViewModel: PlayerViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    mediaMetadataViewModel: AppMediaMetadataViewModel = viewModel(),
    playlistResumeViewModel: PlaylistResumeViewModel = viewModel(),
    emotionViewModel: cn.com.dcsgo.mihx.app.emotion.EmotionViewModel = viewModel(),
    // 情境化随心播放：必须在 Activity 作用域（AppRoot）创建——Hilt @HiltViewModel 依赖
    // Activity 级 Hilt ViewModelFactory；在 NavHost composable{} 内用 viewModel() 会回落到
    // 非 Hilt 的 SavedStateViewModelFactory 而反射空参构造失败（2026-09-04 崩溃回归）。
    // 与 SettingsViewModel/EmotionViewModel 同模式：顶层创建、经参数传入 AppNavHost。
    moodTimeSlotViewModel: cn.com.dcsgo.mihx.app.mood.MoodTimeSlotViewModel = viewModel(),
) {
    val toastHost = rememberToastHost()
    val toastHostCoroutine = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activeRoute = backStackEntry?.destination?.route
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val themeVariant by settingsViewModel.themeVariant.collectAsStateWithLifecycle()
    val lyricFontScale by settingsViewModel.lyricFontScale.collectAsStateWithLifecycle()
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var showQueueSheet by remember { mutableStateOf(false) }
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    LaunchedEffect(activeRoute) {
        // 始终按当前路由所属 Tab 同步高亮（嵌套路由如设置/统计也映射到所属 Tab），
        // 避免从子页面滑动离开再回来后 currentDestination 停留在旧值
        currentDestination = AppDestinations.fromRoute(activeRoute)
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
        // M-3（评审 2026-09-03）：deleteSong 现为 suspend（底层 SAF 跨进程删除已调度到 IO），
        // 调用链整体挂起，避免主线程被 ContentProvider 调用阻塞导致 ANR。
        toastHostCoroutine.launch {
            when (val result = playerViewModel.deleteSong(songId)) {
                is DeleteSongResult.Success -> toastHost.showToast(result.message)
                is DeleteSongResult.Failure -> toastHost.showToast(result.reason)
            }
        }
    }

    MusicplayerTheme(darkTheme = isDarkTheme, variant = themeVariant) {
        SyncSystemBarsAppearance(isDarkTheme)
        // 全站统一的"情绪校准"入口: 任何渲染歌曲详情对话框的页面
        // (曲库/歌手/专辑/本地音乐/播放页/详情页)自动获得"不像？标记"能力
        CompositionLocalProvider(
            LocalEmotionCorrectionController provides remember(emotionViewModel) {
                object : EmotionCorrectionController {
                    override suspend fun save(songId: Int, words: Set<String>): Boolean {
                        val ok = mediaMetadataViewModel.saveEmotionCorrection(songId, words)
                        if (ok) {
                            emotionViewModel.refresh()
                        } else {
                            toastHost.showToast("这首歌还没完成分析")
                        }
                        return ok
                    }
                }
            },
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppScaffold(
                currentDestination = currentDestination,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                positionMs = playerViewModel.positionMs,
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
                // 全屏歌词页不响应横滑，避免误切底部 Tab
                swipeEnabled = activeRoute != AppRoutes.LYRICS,
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
                    playlistResumeViewModel = playlistResumeViewModel,
                    emotionViewModel = emotionViewModel,
                    moodTimeSlotViewModel = moodTimeSlotViewModel,
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
}

@Composable
private fun SyncSystemBarsAppearance(darkTheme: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
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
