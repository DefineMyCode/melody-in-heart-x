package cn.com.dcsgo.mihx.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 播放器底部栏
 *
 * 在非首页底部显示，展示当前播放歌曲信息和基础播放控制。
 *
 * @param currentSong        当前播放的歌曲
 * @param isPlaying          是否正在播放
 * @param onPlayPauseClick   播放/暂停回调
 * @param onPreviousClick    上一首回调
 * @param onNextClick        下一首回调
 * @param onNavigateToHome   点击封面/歌曲信息跳转到首页回调
 */
@Composable
fun MusicPlayerBottomBar(
    currentSong: Song,
    isPlaying: Boolean,
    currentPositionMs: Long = 0L,
    durationMs: Long = 0L,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：封面 + 歌曲信息（可点击跳转首页）
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onNavigateToHome)
        ) {
            if (currentSong.albumArtUri != null) {
                AsyncImage(
                    model = currentSong.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier.size(56.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "正在播放" else "已暂停",
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigateToHome)
        ) {
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 中间：播放控制按钮
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一首
            IconButton(onClick = onPreviousClick) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_previous_24),
                    contentDescription = "上一首"
                )
            }

            // 播放/暂停按钮（外圈进度圆弧）
            val progressTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            val progressColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                // 进度圆弧（64dp 环，包围 48dp 按钮）
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(
                        width = size.width - stroke,
                        height = size.height - stroke,
                    )
                    val topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2)
                    // 底环
                    drawArc(
                        color = progressTrackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    // 进度弧（从 12 点方向顺时针）
                    val progress = if (durationMs > 0) {
                        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    if (progress > 0f) {
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                // 按钮本体
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onPlayPauseClick) {
                        if (isPlaying) {
                            Icon(
                                painter = painterResource(id = R.drawable.pause_24),
                                contentDescription = "暂停",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "播放",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // 下一首
            IconButton(onClick = onNextClick) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_next_24),
                    contentDescription = "下一首"
                )
            }
        }
    }
}
