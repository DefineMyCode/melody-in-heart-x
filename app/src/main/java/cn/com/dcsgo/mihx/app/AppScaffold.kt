package cn.com.dcsgo.mihx.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.app.shell.AppShell
import cn.com.dcsgo.mihx.app.shell.AppTab
import cn.com.dcsgo.mihx.app.shell.LuckyPlayEntryBar
import cn.com.dcsgo.mihx.app.shell.indexOfRoute
import cn.com.dcsgo.mihx.app.shell.playerRoute
import cn.com.dcsgo.mihx.app.shell.shouldShowLuckyPlayEntry
import cn.com.dcsgo.mihx.app.shell.shouldShowMiniPlayer
import cn.com.dcsgo.mihx.app.shell.tabAt
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.player.MusicPlayerBottomBar
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用外壳（自适应：手机底部导航条 / 大屏左侧导航栏）。
 *
 * **P2 改造（2026-09-29）**：底栏从"编译期枚举 `AppDestinations.entries`"改为
 * "运行期 tab 列表 [AppShell.tabs]"，由皮肤描述驱动。改造后每处原有的硬编码
 * 判断都换成等价的数据判断，行为保持一致：
 *
 * | 原先 | 现在 |
 * |---|---|
 * | `AppDestinations.entries.forEach` | `tabs.forEach` |
 * | `currentDestination.ordinal ± 1` | `tabs.indexOfRoute(activeRoute) ± 1` |
 * | `currentDestination != HOME`（迷你条显示条件） | `activeRoute != 播放页路由`（见 [shouldShowMiniPlayer]） |
 *
 * 迷你条条件的**语义**尤其要注意：原实现判断"不属于播放 Tab"，而播放页的路由
 * 可能不在底栏里（抽屉型骨架），所以必须按**路由**判定而非按 tab 归属判定。
 */
@Composable
fun AppScaffold(
    shell: AppShell,
    activeRoute: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: Long,
    onTabSelected: (AppTab) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    swipeEnabled: Boolean = true,
    /**
     * 2026-10-01：随心播放入口条回调（双页样式、队列为空时,迷你条位置显示入口条）。
     *
     * 与 [onNavigateToHome] 一样由 AppRoot 提供（需要 toast/播放列表恢复等 App 级能力）。
     */
    onLuckyPlayClick: () -> Unit = {},
    /**
     * P3：播放抽屉是否正处于打开状态。
     *
     * 抽屉打开时必须隐藏迷你条——否则同一首歌会出现两处控制，且底层条目被遮挡。
     * 这个状态由 AppRoot 持有（抽屉在那儿渲染），以参数下传而不是塞进 [AppShell]：
     * 它表达的是**瞬时的 UI 状态**，不是骨架的结构属性。
     */
    miniPlayerHiddenBySheet: Boolean = false,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp
        if (isExpanded) {
            // 大屏：左侧文字导航栏
            Row(modifier = Modifier.fillMaxSize()) {
                TextNavRail(
                    shell = shell,
                    activeRoute = activeRoute,
                    onTabSelected = onTabSelected,
                )
                ScaffoldContentColumn(
                    shell = shell,
                    activeRoute = activeRoute,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onNavigateToHome = onNavigateToHome,
                    onTabSelected = onTabSelected,
                    swipeEnabled = swipeEnabled,
                    miniPlayerHiddenBySheet = miniPlayerHiddenBySheet,
                    onLuckyPlayClick = onLuckyPlayClick,
                    content = content,
                )
            }
        } else {
            // 手机：顶部内容 + 歌曲条 + 底部文字导航栏
            Column(modifier = Modifier.fillMaxSize()) {
                ScaffoldContentColumn(
                    shell = shell,
                    activeRoute = activeRoute,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onNavigateToHome = onNavigateToHome,
                    onTabSelected = onTabSelected,
                    swipeEnabled = swipeEnabled,
                    onLuckyPlayClick = onLuckyPlayClick,
                    content = content,
                    modifier = Modifier.weight(1f),
                )
                TextBottomBar(
                    shell = shell,
                    activeRoute = activeRoute,
                    onTabSelected = onTabSelected,
                )
            }
        }
    }
}

/**
 * 迷你播放条是否应显示 —— 实现已上提到 `shell` 包（纯策略、可单测），
 * 这里只是 Compose 侧的引用点。详见 [cn.com.dcsgo.mihx.app.shell.shouldShowMiniPlayer]。
 */

@Composable
private fun ScaffoldContentColumn(
    shell: AppShell,
    activeRoute: String?,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    onTabSelected: (AppTab) -> Unit,
    swipeEnabled: Boolean = true,
    miniPlayerHiddenBySheet: Boolean = false,
    /** 随心播放入口条回调（见 [shouldShowLuckyPlayEntry] 的显示条件）。 */
    onLuckyPlayClick: () -> Unit = {},
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                // 内容区左右滑动切换相邻底部 Tab（子节点如进度条/横向标签行消费拖动时自动取消）
                .pointerInput(activeRoute, swipeEnabled, shell.tabs) {
                    if (swipeEnabled && shell.tabs.size > 1) {
                        val swipeThresholdPx = 96.dp.toPx()
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount
                            },
                            onDragEnd = {
                                val direction = when {
                                    // 左滑 → 下一个 Tab；右滑 → 上一个 Tab
                                    totalDragX <= -swipeThresholdPx -> 1
                                    totalDragX >= swipeThresholdPx -> -1
                                    else -> 0
                                }
                                if (direction != 0) {
                                    val targetIndex = shell.tabs.indexOfRoute(activeRoute) + direction
                                    shell.tabs.tabAt(targetIndex)?.let(onTabSelected)
                                }
                            },
                            onDragCancel = { totalDragX = 0f },
                        )
                    }
                },
        ) {
            content()
        }
        if (shouldShowMiniPlayer(shell, activeRoute, currentSong != null) && !miniPlayerHiddenBySheet) {
            MusicPlayerBottomBar(
                isPlaying = isPlaying,
                currentSong = currentSong!!,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onNavigateToHome = onNavigateToHome,
            )
        } else if (
            shouldShowLuckyPlayEntry(shell, activeRoute, currentSong != null) &&
            !miniPlayerHiddenBySheet
        ) {
            // 双页样式、队列为空：迷你条位置改显示「随心播放」入口条（2026-10-01）。
            LuckyPlayEntryBar(onLuckyPlayClick = onLuckyPlayClick)
        }
    }
}

/** 手机端底部文字导航栏（仅文字，紧凑高度） */
@Composable
private fun TextBottomBar(
    shell: AppShell,
    activeRoute: String?,
    onTabSelected: (AppTab) -> Unit,
) {
    val selectedIndex = shell.tabs.indexOfRoute(activeRoute)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shell.tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clickable { onTabSelected(tab) },
            )
        }
    }
}

/** 大屏左侧文字导航栏 */
@Composable
private fun TextNavRail(
    shell: AppShell,
    activeRoute: String?,
    onTabSelected: (AppTab) -> Unit,
) {
    val selectedIndex = shell.tabs.indexOfRoute(activeRoute)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shell.tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
