package cn.com.dcsgo.mihx.app

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import cn.com.dcsgo.mihx.app.player.NowPlayingSurface
import cn.com.dcsgo.mihx.app.player.PlayerQueueSheetHost
import cn.com.dcsgo.mihx.app.player.SongPlaybackStrategy
import cn.com.dcsgo.mihx.app.player.playWith
import cn.com.dcsgo.mihx.app.player.PlayerSheetHost
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.app.theme.SettingsViewModel
import cn.com.dcsgo.mihx.app.shell.AppShell
import cn.com.dcsgo.mihx.app.shell.PlayerEntry
import cn.com.dcsgo.mihx.app.shell.PlayerSheetContent
import cn.com.dcsgo.mihx.app.shell.previousPlayerSheetContent
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRoute
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteActions
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteState
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRoute
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRouteActions
import cn.com.dcsgo.mihx.feature.playlist.AlbumDetailRouteState
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRoute
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRouteActions
import cn.com.dcsgo.mihx.feature.playlist.ArtistDetailRouteState
import cn.com.dcsgo.mihx.app.shell.SkinShellResolver
import cn.com.dcsgo.mihx.app.shell.SkinSwitcherStore
import cn.com.dcsgo.mihx.app.shell.withGridLayout
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.core.model.ScreenOrientationMode
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.navigation.AppRoutes
import cn.com.dcsgo.mihx.ui.components.AutoDismissToasts
import cn.com.dcsgo.mihx.ui.components.EmotionCorrectionController
import cn.com.dcsgo.mihx.ui.components.LocalEmotionCorrectionController
import cn.com.dcsgo.mihx.ui.components.ToastHost
import cn.com.dcsgo.mihx.ui.components.rememberToastHost
import cn.com.dcsgo.mihx.ui.theme.LocalPlaybackPanelTokens
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme
import cn.com.dcsgo.mihx.ui.theme.PlaybackPanelTokens
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
    val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
    val themeVariant by settingsViewModel.themeVariant.collectAsStateWithLifecycle()
    val lyricFontScale by settingsViewModel.lyricFontScale.collectAsStateWithLifecycle()
    val screenOrientationMode by settingsViewModel.screenOrientationMode.collectAsStateWithLifecycle()
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    var showQueueSheet by remember { mutableStateOf(false) }
    // P3：全局播放抽屉的开合状态。
    //
    // ⚠️ 2026-10-24：抽屉**只是浮层**，不进导航栈。
    // 曾经试过把它注册成一条 dialog 目的地（好处是底下的页面不被销毁），
    // 但 dialog 目的地会在系统里生成一个**全屏透明窗口**，
    // 而这个空窗口会把触摸全部吃掉——抽屉整个点不动（真机实测）。
    // 而且它与抽屉窗口的 z-order 取决于两个 StateFlow 谁先到，不可控。
    // 所以回到"浮层 + 手动接线"的形态：抽屉开关不碰导航栈，
    // 底下的页面始终 composed（关抽屉瞬间、不闪白）。
    var showPlayerSheet by remember { mutableStateOf(false) }
    // 抽屉内当前展示的内容（播放页 / 歌词 / 歌手详情 / 专辑详情）。
    //
    // ⚠️ 2026-10-24：详情页也放在抽屉里（和歌词同一层机制），导航不碰主导航栈。
    // 之前把详情页做成导航目的地，"从详情页返回 → 回抽屉"只能靠抢系统返回键，
    // 抢不过 Navigation 2.8 的 NavHost 内部 PredictiveBackHandler（真机实测失败两次）。
    var sheetContent by remember { mutableStateOf<PlayerSheetContent>(PlayerSheetContent.Player) }
    val uiState by playerViewModel.uiState.collectAsStateWithLifecycle()

    // 全库分组结果 AppRoot 级共享：曲库/歌单详情/歌手/专辑/榜单/版本管理等路由
    // 原先在 NavHost 层各自 remember(uiState.songs) 重复执行 O(n) 分组，1100+ 首曲库下切页即重算。
    // 现在抽屉内也要渲染歌手/专辑详情，故提升到 AppRoot 只算一次，按 songs 列表实例失效。
    val sharedLibrarySongs = remember(uiState.songs) {
        flatGroupedSongs(uiState, playerViewModel)
    }

    // 样式切换持久化：当前选中的内置 skinId + 播放面板封面尺寸/圆角的覆盖。
    // 用 SharedPreferences 单文件 (skin_switcher)；覆盖按 skinId 分键，仅当前选中的 id 有意义。
    val context = LocalContext.current
    // 屏幕方向设置：应用当前配置到 Activity（自动/横屏/竖屏）
    LaunchedEffect(screenOrientationMode) {
        val activity = context as? Activity ?: return@LaunchedEffect
        val orientation = when (screenOrientationMode) {
            ScreenOrientationMode.SENSOR_AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            ScreenOrientationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            ScreenOrientationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
        activity.requestedOrientation = orientation
    }
    val skinStore = remember(context) { SkinSwitcherStore(context.applicationContext) }
    var currentSkinId by remember {
        // 样式列表收紧到「默认三页 / 极简双页」两套(2026-09-30)后,旧 prefs 里可能存着
        // 已下线的 id(网格/黑胶/水墨/网易云式)——不在列表里就回落默认,否则切换页无项高亮。
        mutableStateOf(
            skinStore.activeId()
                ?.takeIf { id -> SkinShellResolver.knownSkins.any { it.id == id } }
                ?: SkinShellResolver.DEFAULT_SKIN_ID,
        )
    }
    var panelCoverSize by remember {
        mutableStateOf<Float?>(skinStore.panelCoverSize(currentSkinId))
    }
    var panelCoverCorner by remember {
        mutableStateOf<Float?>(skinStore.panelCoverCorner(currentSkinId))
    }
    // 歌手/专辑网格布局：全局开关（不分样式），默认关 = 改造前的列表行形态。
    var gridLayoutEnabled by remember {
        mutableStateOf(skinStore.gridLayoutEnabled())
    }
    val onGridLayoutChange: (Boolean) -> Unit = { enabled ->
        gridLayoutEnabled = enabled
        skinStore.setGridLayoutEnabled(enabled)
    }
    // 解析外壳：当前选中是内置 id → resolveById(原有路径,行为零变化)。
    // 不再有「user.skin.*」分支——用户自定义皮肤已被样式切换取代。
    val shell: AppShell = remember(currentSkinId, gridLayoutEnabled) {
        SkinShellResolver.resolveById(currentSkinId).withGridLayout(gridLayoutEnabled)
    }

    BackHandler(enabled = showQueueSheet) {
        showQueueSheet = false
    }

    // 监听返回栈：从抽屉跳出去的那一页**离栈**了 → 回到播放抽屉。
    if (uiState.isLoading) {
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

    // 样式切换回调:用户切样式 → 写入 prefs + 把播放面板覆盖值切到新 id 的覆盖。
    val onSkinSelected: (String) -> Unit = { id ->
        currentSkinId = id
        skinStore.setActiveId(id)
        panelCoverSize = skinStore.panelCoverSize(id)
        panelCoverCorner = skinStore.panelCoverCorner(id)
    }
    // 播放面板覆盖值变化 → 写入 prefs(按当前选中 id 分键)。
    val onPanelCoverSizeChange: (Float) -> Unit = { v ->
        panelCoverSize = v
        skinStore.setPanelCoverSize(currentSkinId, v)
    }
    val onPanelCoverCornerChange: (Float) -> Unit = { v ->
        panelCoverCorner = v
        skinStore.setPanelCoverCorner(currentSkinId, v)
    }

    MusicplayerTheme(darkTheme = isDarkTheme, variant = themeVariant) {
        SyncSystemBarsAppearance(isDarkTheme)
        // 样式切换里保留的「播放面板封面边长/圆角」通过 LocalPlaybackPanelTokens 实时下发:
        //  - 任一为 null → 落到默认 PlaybackPanelTokens()(与改造前硬编码一致)
        //  - 任一非 null → 用对应值,其余字段仍走默认值(当前仅这两个旋钮被样式切换覆盖)
        val panelOverride = PlaybackPanelTokens(
            coverSizeDp = panelCoverSize ?: PlaybackPanelTokens().coverSizeDp,
            coverCornerDp = panelCoverCorner ?: PlaybackPanelTokens().coverCornerDp,
        )
        CompositionLocalProvider(LocalPlaybackPanelTokens provides panelOverride) {
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
                shell = shell,
                activeRoute = activeRoute,
                currentSong = uiState.currentSong,
                isPlaying = uiState.isPlaying,
                positionMs = playerViewModel.positionMs,
                durationMs = uiState.durationMs,
                // P3：抽屉打开时隐藏迷你条——否则同一首歌出现两处控制，且底层条目被遮挡
                miniPlayerHiddenBySheet = showPlayerSheet,
                onTabSelected = { tab ->
                    navController.navigate(tab.route) {
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
                // 双页样式、队列为空:入口条触发随心播放(逻辑与播放页 FAB 同源,照抄 NowPlayingSurface)。
                onLuckyPlayClick = {
                    playerViewModel.playRandomQueue { started, moodSlotName ->
                        if (started) {
                            // 情境化随心播放归因(§4.5):让"这首歌为什么被选中"可解释
                            moodSlotName?.let { slotName ->
                                toastHost.showToast("已按「$slotName」为你随机播放")
                            }
                        } else {
                            toastHost.showToast("还没有可播放的音乐，请先导入歌曲吧~")
                        }
                    }
                    playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                },
                onNavigateToHome = {
                    // P3：抽屉型骨架没有"播放页 Tab"，迷你条点击的语义变成"拉起抽屉"；
                    // 默认骨架（TAB）保持原语义不变（导航到播放页）。
                    if (shell.playerEntry == PlayerEntry.SHEET) {
                        // 从迷你条拉起抽屉：重置到播放页（可能上次停在详情/歌词）
                        sheetContent = PlayerSheetContent.Player
                        showPlayerSheet = true
                    } else {
                        navController.navigate(AppRoutes.HOME) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                // 全屏歌词页不响应横滑，避免误切底部 Tab
                swipeEnabled = activeRoute != AppRoutes.LYRICS,
            ) {
                AppNavHost(
                    navController = navController,
                    shell = shell,
                    uiState = uiState,
                    playerViewModel = playerViewModel,
                    permissionCoordinator = permissionCoordinator,
                    onShowQueue = { showQueueSheet = true },
                    // 全库分组（抽屉内的歌手/专辑详情也要用，故在 AppRoot 算一次）
                    sharedLibrarySongs = sharedLibrarySongs,
                    themeMode = themeMode,
                    onThemeModeChange = settingsViewModel::setThemeMode,
                    themeVariant = themeVariant,
                    onThemeVariantChange = settingsViewModel::setThemeVariant,
                    screenOrientationMode = screenOrientationMode,
                    onScreenOrientationModeChange = settingsViewModel::setScreenOrientationMode,
                    lyricFontScale = lyricFontScale,
                    onLyricFontScaleChange = settingsViewModel::setLyricFontScale,
                    loadLyrics = mediaMetadataViewModel::lyricsFor,
                    loadSongInfo = mediaMetadataViewModel::songInfo,
                    showToast = toastHost::showToast,
                    deleteSongWithToast = ::deleteSongWithToast,
                    playlistResumeViewModel = playlistResumeViewModel,
                    emotionViewModel = emotionViewModel,
                    moodTimeSlotViewModel = moodTimeSlotViewModel,
                    // 样式切换:从 AppRoot 把"当前选中 id / 切样式回调 / 播放面板覆盖回调"
                    // 透传到样式切换页(SKIN_SWITCHER 路由)。
                    currentSkinId = currentSkinId,
                    onSkinSelected = onSkinSelected,
                    panelCoverSizeOverride = panelCoverSize,
                    panelCoverCornerOverride = panelCoverCorner,
                    onPanelCoverSizeChange = onPanelCoverSizeChange,
                    onPanelCoverCornerChange = onPanelCoverCornerChange,
                    gridLayoutEnabled = gridLayoutEnabled,
                    onGridLayoutChange = onGridLayoutChange,
                )
            }

            // P3：全局播放抽屉。仅当骨架声明 playerEntry = SHEET 时可能为 true。
            //
            // 抽屉是**全局浮层**，内部自己维护一层内容栈（播放页 / 歌词 / 歌手 / 专辑，
            // 见 `PlayerSheetContent`）——导航**完全不碰主导航栈**：
            //  - 系统返回键由抽屉自己的窗口消费 → onDismiss → 退回上一层内容；
            //  - 页面内返回按钮走同一套内容切换；
            // 两条路天然一致，也不存在与 NavHost 的 PredictiveBackHandler 抢返回键的问题
            //（2026-10-24 真机连踩两次，见 P3 文档的"弯路"一节）。
            PlayerSheetHost(
                isShown = showPlayerSheet,
                // sheet 内挂焦点协调的 ToastHost:只让聚焦窗口画,不双 toast 也不被遮(2026-10-01)。
                toastHost = toastHost,
                // 返回：先退一层内容（详情 → 播放页），退到底才关抽屉。
                onDismiss = {
                    val previous = previousPlayerSheetContent(sheetContent)
                    if (previous != null) {
                        sheetContent = previous
                    } else {
                        showPlayerSheet = false
                    }
                },
                // 系统返回键：抽屉内还有上一层（详情/歌词）时，退回上一层而不是关抽屉。
                // 接线在 PlayerSheetHost 的**内容里**（它比 M3 自己那条注册得晚 → 优先级更高）。
                hasInnerContent = previousPlayerSheetContent(sheetContent) != null,
                onBackToPreviousContent = {
                    sheetContent = previousPlayerSheetContent(sheetContent)
                        ?: PlayerSheetContent.Player
                },
            ) {
                when (val page = sheetContent) {
                    is PlayerSheetContent.Player -> NowPlayingSurface(
                        playerViewModel = playerViewModel,
                        uiState = uiState,
                        onShowQueue = { showQueueSheet = true },
                        loadSongInfo = mediaMetadataViewModel::songInfo,
                        showToast = toastHost::showToast,
                        deleteSongWithToast = ::deleteSongWithToast,
                        playlistResumeViewModel = playlistResumeViewModel,
                        topContentPaddingDp = 16.dp,
                        onNavigateToLyrics = { sheetContent = PlayerSheetContent.Lyrics },
                        onNavigateToArtist = { artistName ->
                            sheetContent = PlayerSheetContent.Artist(artistName)
                        },
                        onNavigateToAlbum = { albumName ->
                            sheetContent = PlayerSheetContent.Album(albumName)
                        },
                    )

                    is PlayerSheetContent.Lyrics -> LyricsRoute(
                        state = LyricsRouteState(
                            currentSong = uiState.currentSong,
                            currentPositionMs = playerViewModel.positionMs
                                .collectAsStateWithLifecycle().value,
                            isPlaying = uiState.isPlaying,
                            fontScale = lyricFontScale,
                        ),
                        actions = LyricsRouteActions(
                            onBackClick = { sheetContent = PlayerSheetContent.Player },
                            onSeekTo = playerViewModel::seekTo,
                            onFontScaleChange = settingsViewModel::setLyricFontScale,
                        ),
                        loadLyrics = mediaMetadataViewModel::lyricsFor,
                    )

                    // ⚠️ `key(名字)`：详情页内部的分段选择器是 rememberSaveable，
                    // 抽屉里所有详情页组合在同一个槽位 → 不加 key 的话，
                    // 从专辑 A 切到专辑 B 会**沿用** A 的分段（停在"其他专辑"而不是"歌曲"）。
                    // 加 key 后换页即换组合槽，保存态自然重置 ✓
                    is PlayerSheetContent.Artist -> key(page.name) {
                        ArtistDetailRoute(
                            state = ArtistDetailRouteState(
                                artistName = page.name,
                                songs = sharedLibrarySongs,
                                playlists = uiState.playlists,
                                currentSong = uiState.currentSong,
                                isPlaying = uiState.isPlaying,
                            ),
                            actions = ArtistDetailRouteActions(
                                onBack = { sheetContent = PlayerSheetContent.Player },
                                onSongClick = { song, contextSongs ->
                                    playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
                                    playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                                },
                                onAlbumClick = { albumName ->
                                    sheetContent = PlayerSheetContent.Album(albumName)
                                },
                                onAddSongToPlaylist = { song, playlist ->
                                    playerViewModel.addSongToPlaylist(playlist.id, song.id)
                                },
                                onAddSongsToPlaylist = { songs, playlist ->
                                    songs.count { song ->
                                        playerViewModel.addSongToPlaylist(playlist.id, song.id)
                                    }
                                },
                                onCreatePlaylistWithResult = playerViewModel::createPlaylist,
                            ),
                            loadSongInfo = mediaMetadataViewModel::songInfo,
                            showToast = toastHost::showToast,
                        )
                    }

                    is PlayerSheetContent.Album -> key(page.name) {
                        AlbumDetailRoute(
                            state = AlbumDetailRouteState(
                                albumName = page.name,
                                songs = sharedLibrarySongs,
                                playlists = uiState.playlists,
                                currentSong = uiState.currentSong,
                                isPlaying = uiState.isPlaying,
                            ),
                            actions = AlbumDetailRouteActions(
                                onBack = { sheetContent = PlayerSheetContent.Player },
                                onSongClick = { song, contextSongs ->
                                    playerViewModel.playWith(song, SongPlaybackStrategy.scope(contextSongs))
                                    playlistResumeViewModel.switchSource(null, uiState.currentSong?.id)
                                },
                                onAlbumClick = { albumName ->
                                    sheetContent = PlayerSheetContent.Album(albumName)
                                },
                                onAddSongToPlaylist = { song, playlist ->
                                    playerViewModel.addSongToPlaylist(playlist.id, song.id)
                                },
                                onAddSongsToPlaylist = { songs, playlist ->
                                    songs.count { song ->
                                        playerViewModel.addSongToPlaylist(playlist.id, song.id)
                                    }
                                },
                                onCreatePlaylistWithResult = playerViewModel::createPlaylist,
                            ),
                            loadSongInfo = mediaMetadataViewModel::songInfo,
                            showToast = toastHost::showToast,
                        )
                    }
                }
            }

            PlayerQueueSheetHost(
                playQueue = uiState.playQueue,
                isShown = showQueueSheet,
                // 同抽屉:队列 sheet 内也挂焦点协调的 ToastHost。
                toastHost = toastHost,
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
