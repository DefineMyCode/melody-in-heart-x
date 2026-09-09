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
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.player.MusicPlayerBottomBar
import cn.com.dcsgo.mihx.navigation.AppDestinations
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AppScaffold(
    currentDestination: AppDestinations,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: Long,
    onDestinationSelected: (AppDestinations) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    /** 点击迷你条 → 拉出播放全屏抽屉（方案D实验） */
    onOpenPlayerSheet: () -> Unit = {},
    /** 空曲库时全局「随心播放」入口（列表空、迷你条不存在时的播放入口） */
    onLuckyPlayClick: (() -> Unit)? = null,
    /** 播放抽屉展开时隐藏底栏/迷你条（抽屉盖住全屏，底下露出底栏会穿帮） */
    hideBottomBars: Boolean = false,
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 600.dp
        if (isExpanded) {
            // 大屏：左侧文字导航栏
            Row(modifier = Modifier.fillMaxSize()) {
                TextNavRail(
                    currentDestination = currentDestination,
                    onDestinationSelected = onDestinationSelected,
                )
                ScaffoldContentColumn(
                    currentDestination = currentDestination,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onOpenPlayerSheet = onOpenPlayerSheet,
                    onLuckyPlayClick = onLuckyPlayClick,
                    onDestinationSelected = onDestinationSelected,
                    hideBottomBars = hideBottomBars,
                    swipeEnabled = swipeEnabled,
                    content = content,
                )
            }
        } else {
            // 手机：顶部内容 + 歌曲条 + 底部文字导航栏
            Column(modifier = Modifier.fillMaxSize()) {
                ScaffoldContentColumn(
                    currentDestination = currentDestination,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onOpenPlayerSheet = onOpenPlayerSheet,
                    onLuckyPlayClick = onLuckyPlayClick,
                    onDestinationSelected = onDestinationSelected,
                    hideBottomBars = hideBottomBars,
                    swipeEnabled = swipeEnabled,
                    content = content,
                    modifier = Modifier.weight(1f),
                )
                if (!hideBottomBars) {
                    TextBottomBar(
                        currentDestination = currentDestination,
                        onDestinationSelected = onDestinationSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaffoldContentColumn(
    currentDestination: AppDestinations,
    currentSong: Song?,
    isPlaying: Boolean,
    positionMs: StateFlow<Long>,
    durationMs: Long,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onOpenPlayerSheet: () -> Unit,
    onLuckyPlayClick: (() -> Unit)?,
    onDestinationSelected: (AppDestinations) -> Unit,
    hideBottomBars: Boolean = false,
    swipeEnabled: Boolean = true,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            // 播放浮层打开时不加状态栏 padding（浮层要全屏铺到状态栏后面）
            .then(if (hideBottomBars) Modifier else Modifier.statusBarsPadding()),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface)
                // 内容区左右滑动切换相邻底部 Tab（子节点如进度条/横向标签行消费拖动时自动取消）
                .pointerInput(currentDestination, swipeEnabled) {
                    if (swipeEnabled) {
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
                                    // 横滑只在底栏 Tab（曲库/我的）间切换；
                                    // HOME 是全屏抽屉承载，不参与 Tab 序（方案D）
                                    val tabs = AppDestinations.entries.filter { it.showInBottomBar }
                                    val currentIdx = tabs.indexOf(currentDestination)
                                    if (currentIdx >= 0) {
                                        val target = (currentIdx + direction).mod(tabs.size)
                                        onDestinationSelected(tabs[target])
                                    }
                                }
                            },
                            onDragCancel = { totalDragX = 0f },
                        )
                    }
                },
        ) {
            content()
        }
        // 迷你条：非播放页 + 有歌 + 抽屉未展开。点击整体拉出播放抽屉
        if (!hideBottomBars && currentSong != null) {
            MusicPlayerBottomBar(
                isPlaying = isPlaying,
                currentSong = currentSong,
                positionMs = positionMs,
                durationMs = durationMs,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onNavigateToHome = onOpenPlayerSheet,
            )
        }
        // 空曲库兜底入口：迷你条不存在时，提供全局「随心播放」让用户能开始播放
        if (!hideBottomBars && currentSong == null && onLuckyPlayClick != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .navigationBarsPadding()
                    .clickable(onClick = onLuckyPlayClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "∞ 随心播放",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "  ·  从曲库随机开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 手机端底部文字导航栏（仅文字，紧凑高度） */
@Composable
private fun TextBottomBar(
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppDestinations.entries.filter { it.showInBottomBar }.forEach { destination ->
            val selected = destination == currentDestination
            Text(
                text = destination.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clickable { onDestinationSelected(destination) },
            )
        }
    }
}

/** 大屏左侧文字导航栏 */
@Composable
private fun TextNavRail(
    currentDestination: AppDestinations,
    onDestinationSelected: (AppDestinations) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppDestinations.entries.filter { it.showInBottomBar }.forEach { destination ->
            val selected = destination == currentDestination
            Text(
                text = destination.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDestinationSelected(destination) }
                    .padding(vertical = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
