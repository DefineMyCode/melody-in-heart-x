package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.common.time.formatDurationTime
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 首页（播放器主界面）
 *
 * 显示当前播放歌曲的封面、歌曲信息、进度条、播放控制，
 * 以及同名版本选择器和歌词视图。
 */
@Composable
fun HomeScreen(
    currentSong: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    isInfinitePlay: Boolean = false,
    sameNameSongs: List<Song> = emptyList(),
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onStartSeeking: () -> Unit,
    onEndSeeking: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueClick: () -> Unit,
    onShowLyrics: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onSwitchVersion: (Song) -> Unit = {},
    onTextCopied: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onLuckyPlayClick: () -> Unit = {},
    onInfinitePlayClick: () -> Unit = {},
    onRelatedPlayClick: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    sleepTimerRemainingMs: Long = 0L,
    sleepTimerPlayLastSong: Boolean = false,
    sleepTimerPausePending: Boolean = false,
    onSleepTimerStart: (Int, Boolean) -> Unit = { _, _ -> },
    onSleepTimerCancel: () -> Unit = {},
) {
    if (currentSong == null) {
        // 空状态：没有任何音乐，仍显示 FAB
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            EmptyHomeHint()
            HomeFabs(
                onLuckyPlayClick = onLuckyPlayClick
            )
        }
    } else {
        // 封面视图：用 Box 包裹以叠加 FAB 和 Hi-Res 徽章
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item(key = "album_cover") {
                    AlbumCoverSection(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onCoverClick = onShowLyrics
                    )
                }

                item(key = "song_info") {
                    SongInfoSection(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        playMode = playMode,
                        isInfinitePlay = isInfinitePlay,
                        sameNameSongs = sameNameSongs,
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        onPlayPauseClick = onPlayPauseClick,
                        onPreviousClick = onPreviousClick,
                        onNextClick = onNextClick,
                        onStartSeeking = onStartSeeking,
                        onEndSeeking = onEndSeeking,
                        onSeekTo = onSeekTo,
                        onQueueClick = onQueueClick,
                        onTogglePlayMode = onTogglePlayMode,
                        onSwitchVersion = onSwitchVersion,
                        onTextCopied = onTextCopied,
                        onInfinitePlayClick = onInfinitePlayClick,
                        onRelatedPlayClick = onRelatedPlayClick,
                        isSleepTimerActive = isSleepTimerActive,
                        sleepTimerRemainingMs = sleepTimerRemainingMs,
                        sleepTimerPlayLastSong = sleepTimerPlayLastSong,
                        sleepTimerPausePending = sleepTimerPausePending,
                        onSleepTimerStart = onSleepTimerStart,
                        onSleepTimerCancel = onSleepTimerCancel,
                    )
                }
            }

            // 左上角 Hi-Res 徽章
            if (currentSong.sampleRate >= 88200) {
                HiResBadge(
                    sampleRate = currentSong.sampleRate,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 16.dp)
                )
            }

            HomeFabs(
                onLuckyPlayClick = onLuckyPlayClick
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 首页 FAB：右下「随机低播放量」
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BoxScope.HomeFabs(
    onLuckyPlayClick: () -> Unit,
) {
    // 对齐设计系统 §5.11：胶囊形 FAB，accent 底 + 文字标签 + 阴影
    ExtendedFloatingActionButton(
        onClick = onLuckyPlayClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(25.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.shuffle_24),
                contentDescription = "随机播放低播放量歌曲",
                modifier = Modifier.size(20.dp)
            )
        },
        text = {
            Text(
                text = "随心播放",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
    )
}

@Composable
private fun EmptyHomeHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "还没有音乐可播放",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "前往「曲库」页面，\n添加本地音乐文件或添加歌曲到播放队列吧~",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 专辑封面区域（可点击切换到歌词视图）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlbumCoverSection(
    currentSong: Song,
    isPlaying: Boolean,
    onCoverClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(252.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCoverClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (currentSong.albumArtUri != null) {
                // 真实专辑封面
                AsyncImage(
                    model = currentSong.albumArtUri,
                    contentDescription = "专辑封面，点击查看歌词",
                    modifier = Modifier.size(252.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 无封面时的占位图标
                Box(
                    modifier = Modifier
                        .size(252.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 封面顶部高光（近似设计 §5.9 径向光照）
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.14f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // 点击提示
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "点击查看歌词",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌曲信息与播放控制栏
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SongInfoSection(
    currentSong: Song,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    isInfinitePlay: Boolean,
    sameNameSongs: List<Song>,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onStartSeeking: () -> Unit,
    onEndSeeking: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueClick: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onSwitchVersion: (Song) -> Unit,
    onTextCopied: (String) -> Unit,
    onInfinitePlayClick: () -> Unit,
    onRelatedPlayClick: () -> Unit,
    isSleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    sleepTimerPlayLastSong: Boolean,
    sleepTimerPausePending: Boolean,
    onSleepTimerStart: (Int, Boolean) -> Unit,
    onSleepTimerCancel: () -> Unit,
) {
    // 专辑名直接来自 Song 模型（导入时已记录）
    val albumName = currentSong.album.takeIf { it.isNotBlank() }
    // 拆分后的歌手列表
    val artists = currentSong.parsedArtists
    // 是否弹出多歌手选择
    var showArtistPicker by remember { mutableStateOf(false) }
    // 是否弹出定时关闭设置弹窗
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 歌曲名（带复制按钮）
        CopyableText(
            text = currentSong.title,
            isTitle = true,
            onCopied = onTextCopied
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 艺术家名（带复制按钮，可点击跳转歌手详情；多歌手时先选择）
        CopyableText(
            text = currentSong.artist,
            isTitle = false,
            onClick = {
                if (artists.size > 1) {
                    showArtistPicker = true
                } else {
                    artists.firstOrNull()?.let(onArtistClick)
                }
            },
            onCopied = onTextCopied
        )

        // 专辑名（带复制按钮，可点击跳转专辑详情）
        if (albumName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            CopyableText(
                text = albumName,
                isTitle = false,
                onClick = { onAlbumClick(albumName) },
                onCopied = onTextCopied
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 同名歌曲版本选择器
        VersionSelector(
            sameNameSongs = sameNameSongs,
            currentSongId = currentSong.id,
            onVersionSelect = onSwitchVersion
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 播放进度条
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 本地状态：跟踪拖动中的进度
            var localPositionMs by remember { mutableStateOf(currentPositionMs.toFloat()) }
            var isDragging by remember { mutableStateOf(false) }

            // 拖动时用 localPositionMs，非拖动时直接用 currentPositionMs
            // 这样拖动期间不会因播放进度更新而跳动
            val sliderValue = if (isDragging) localPositionMs else currentPositionMs.toFloat()

            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    if (!isDragging) {
                        isDragging = true
                        onStartSeeking()
                        localPositionMs = newValue
                    } else {
                        localPositionMs = newValue
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                    onEndSeeking(localPositionMs.toLong())
                },
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    // 对齐设计系统 §5.4：未完成轨道用 out1（outlineVariant）
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationTime(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationTime(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 播放控制栏（队列按钮 + 上一首 + 暂停 + 下一首 + 播放模式按钮）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放队列入口（暂停按钮左侧）
            IconButton(onClick = onQueueClick) {
                Icon(
                    painter = painterResource(id = R.drawable.queue_music_24),
                    contentDescription = "播放队列",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 上一首
            IconButton(onClick = onPreviousClick) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_previous_24),
                    contentDescription = "上一首",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 播放/暂停按钮
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onPlayPauseClick) {
                    if (isPlaying) {
                        Icon(
                            painter = painterResource(id = R.drawable.pause_24),
                            contentDescription = "暂停",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // 下一首
            IconButton(onClick = onNextClick) {
                Icon(
                    painter = painterResource(id = R.drawable.skip_next_24),
                    contentDescription = "下一首",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 播放模式切换（暂停按钮右侧）
            IconButton(onClick = onTogglePlayMode) {
                Icon(
                    painter = painterResource(id = playMode.icon),
                    contentDescription = "播放模式: ${playMode.label}",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 无限随机播放 + 关联播放按钮（同一行）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            // 无限随机播放按钮：使用FilterChip样式
            androidx.compose.material3.FilterChip(
                selected = isInfinitePlay,
                onClick = onInfinitePlayClick,
                label = {
                    Text(
                        text = if (isInfinitePlay) "无限随机播放中" else "无限随机播放",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.all_inclusive_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 关联播放按钮
            androidx.compose.material3.FilterChip(
                selected = false,
                onClick = onRelatedPlayClick,
                label = {
                    Text(
                        text = "关联播放",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 定时关闭入口（无限随机播放 / 关联播放的下一行）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            SleepTimerChip(
                isActive = isSleepTimerActive,
                remainingMs = sleepTimerRemainingMs,
                pausePending = sleepTimerPausePending,
                onClick = { showSleepTimerDialog = true }
            )
        }
    }

    // 定时关闭设置弹窗
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            isActive = isSleepTimerActive,
            remainingMs = sleepTimerRemainingMs,
            playLastSong = sleepTimerPlayLastSong,
            onDismiss = { showSleepTimerDialog = false },
            onStart = { minutes, playLast -> onSleepTimerStart(minutes, playLast) },
            onCancel = onSleepTimerCancel,
        )
    }

    // 多歌手选择弹窗：点击歌手时选择要跳转的歌手
    if (showArtistPicker) {
        AlertDialog(
            onDismissRequest = { showArtistPicker = false },
            title = { Text("选择歌手") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    artists.forEach { artist ->
                        TextButton(
                            onClick = {
                                showArtistPicker = false
                                onArtistClick(artist)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showArtistPicker = false }) { Text("取消") }
            }
        )
    }
}
