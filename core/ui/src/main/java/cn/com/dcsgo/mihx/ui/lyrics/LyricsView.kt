package cn.com.dcsgo.mihx.ui.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.dcsgo.mihx.core.model.Lyrics

/**
 * 歌词展示组件
 *
 * 卡拉OK风格：当前行高亮大号、上下渐隐、自动居中滚动。
 * 点击歌词行可跳转到对应播放位置。
 * 点击空白区域返回封面视图。
 *
 * @param songTitle 歌曲标题
 * @param songArtist 歌曲艺术家
 * @param lyrics 歌词数据
 * @param currentTimeMs 当前播放时间（毫秒）
 * @param isPlaying 当前是否在播放
 * @param onBackClick 点击返回按钮的回调
 * @param onSeekTo 点击歌词行跳转播放位置的回调
 */
@Composable
fun LyricsView(
    songTitle: String,
    songArtist: String,
    lyrics: Lyrics,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onBackClick: () -> Unit,
    onSeekTo: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
    fontScale: Float = DEFAULT_FONT_SCALE,
    onFontScaleChange: (Float) -> Unit = {},
) {
    val listState = rememberLazyListState()

    // 系统返回键支持
    BackHandler { onBackClick() }

    // 是否有时间戳歌词（timeMs 不全为 0）
    val hasTimestamps = remember(lyrics) {
        lyrics.lines.any { it.timeMs > 0 }
    }

    // 获取当前高亮的歌词行索引
    val currentLineIndex = lyrics.getCurrentLineIndex(currentTimeMs)

    // 当当前行索引变化时，自动滚动到该行
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && lyrics.lines.isNotEmpty()) {
            // 让当前行大致居中，仅保留前一行在屏幕上
            val targetIndex = currentLineIndex.coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (lyrics.lines.isEmpty()) {
            // 无歌词状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "暂无歌词",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "点击任意处返回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClick
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = songTitle.ifEmpty { "无标题" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = songArtist.ifEmpty { "未知艺术家" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // 字号调节按钮
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onFontScaleChange((fontScale - FONT_SCALE_STEP).coerceAtLeast(MIN_FONT_SCALE)) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextDecrease,
                                    contentDescription = "减小字号",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onFontScaleChange(DEFAULT_FONT_SCALE) }
                            ) {
                                Text(
                                    text = "A",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.semantics { contentDescription = "恢复默认字号" }
                                )
                            }
                            IconButton(
                                onClick = { onFontScaleChange((fontScale + FONT_SCALE_STEP).coerceAtMost(MAX_FONT_SCALE)) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TextIncrease,
                                    contentDescription = "加大字号",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 歌词列表区域
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onBackClick() }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // 顶部间距：让第一行歌词可以从屏幕中下方开始
                        item(key = "lyrics_top_spacer") { Spacer(modifier = Modifier.height(80.dp)) }

                        itemsIndexed(
                            items = lyrics.lines,
                            key = { index, line -> "${line.timeMs}_$index" }
                        ) { index, line ->
                            LyricLineItem(
                                text = line.text,
                                isCurrentLine = index == currentLineIndex,
                                isPastLine = index < currentLineIndex,
                                isNearCurrent = hasTimestamps && (index == currentLineIndex - 1),
                                fontScale = fontScale,
                                onClick = {
                                    if (hasTimestamps && onSeekTo != null && line.timeMs > 0) {
                                        onSeekTo(line.timeMs)
                                    }
                                }
                            )
                        }

                        // 底部间距
                        item(key = "lyrics_bottom_spacer") { Spacer(modifier = Modifier.height(300.dp)) }
                    }

                    // 顶部渐隐遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // 底部渐隐遮罩
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

/**
 * 单行歌词组件
 *
 * 当前行：大号、加粗、主题色、全不透明
 * 前一行：稍微大一点、半透明（过渡效果）
 * 过去行：小号、低透明度
 * 未来行：小号、中等透明度
 */
@Composable
private fun LyricLineItem(
    text: String,
    isCurrentLine: Boolean,
    isPastLine: Boolean,
    isNearCurrent: Boolean = false,
    fontScale: Float = DEFAULT_FONT_SCALE,
    onClick: (() -> Unit)? = null
) {
    // 不透明度动画
    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrentLine -> 1f
            isPastLine -> 0.3f
            else -> 0.55f
        },
        animationSpec = tween(durationMillis = 400),
        label = "lyric_alpha"
    )

    // 字号动画（乘以缩放倍率）
    val fontSize by animateFloatAsState(
        targetValue = when {
            isCurrentLine -> 22f * fontScale
            isNearCurrent -> 18f * fontScale
            else -> 16f * fontScale
        },
        animationSpec = tween(durationMillis = 400),
        label = "lyric_size"
    )

    // 字重
    val fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal

    // 颜色
    val textColor = if (isCurrentLine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .alpha(alpha)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            lineHeight = (fontSize * 1.6f).sp
        ),
        color = textColor,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

private const val DEFAULT_FONT_SCALE = 1f
private const val MIN_FONT_SCALE = 0.7f
private const val MAX_FONT_SCALE = 1.8f
private const val FONT_SCALE_STEP = 0.1f
