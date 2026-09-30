package cn.com.dcsgo.mihx.app.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.feature.player.PlayerUiState
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.DeleteSongConfirmDialog
import cn.com.dcsgo.mihx.ui.components.SingleSongAddToPlaylistDialog
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import android.net.Uri

/**
 * 黑胶形态的"现在播放"页面（L4 播放页形态，2026-09-30）。
 *
 * 设计原则：
 *  - **不重造状态机**：与 [NowPlayingSurface] 共用 PlayerViewModel / PlayerUiState，所有
 *    播放控制/秒切/模式切换/歌词跳转走原回调。
 *  - **不引入新回调**：参数签名与 NowPlayingSurface 完全相同。
 *  - **守住 L5/L6 范围线**：黑胶 = 旋转封面 + 进度环 + 最简控制条；
 *    **不做频谱/不做波形**（L6 表现层零件，用户拍板不做）。
 *
 * 视觉编排（自上而下）：状态文字 → 黑胶封面（旋转）→ 标题/歌手 → 进度环 → 控制条。
 *
 * 与 classic 的关系：默认骨架仍走 [NowPlayingSurface]；皮肤描述里
 * `pages.player.template = "vinyl"` 时本形态替换它（由 AppNavHost 分发）。
 */
@Composable
fun NowPlayingVinylSurface(
    playerViewModel: PlayerViewModel,
    uiState: PlayerUiState,
    onShowQueue: () -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
    deleteSongWithToast: (Int) -> Unit,
    playlistResumeViewModel: PlaylistResumeViewModel,
    onNavigateToLyrics: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
) {
    val positionMs by playerViewModel.positionMs.collectAsStateWithLifecycle()
    val currentSong = uiState.currentSong
    val isPlaying = uiState.isPlaying
    val durationMs = uiState.durationMs

    // 黑胶封面旋转：播放时转,暂停/无歌时停
    val transition = rememberInfiniteTransition(label = "vinyl")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vinyl-rotation",
    )
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 更多菜单(三个对话框),与 classic 共用同一组状态变量命名
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    var songForAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    var songForDelete by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(songForInfo) {
        if (songForInfo != null) {
            songInfo = runCatching { songForInfo?.let { loadSongInfo(it) } }.getOrNull()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "现在播放",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onShowQueue) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            VinylCover(
                albumArtUri = currentSong?.albumArtUri,
                rotationDegrees = if (isPlaying && currentSong != null) rotation else 0f,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = currentSong?.title ?: "尚未选择歌曲",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                val artist = currentSong?.parsedArtists?.joinToString(" / ")?.takeIf { it.isNotBlank() }
                val album = currentSong?.album?.takeIf { it.isNotBlank() }
                if (artist != null) append(artist)
                if (album != null) {
                    if (isNotEmpty()) append(" · ")
                    append(album)
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            VinylProgressRing(
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
            )

            Spacer(modifier = Modifier.height(4.dp))

            ControlBar(
                isPlaying = isPlaying,
                canControl = currentSong != null,
                playModeLabel = uiState.playQueue.playMode.label,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onPrev = { playerViewModel.playPrevious() },
                onNext = { playerViewModel.playNext() },
                onQueue = onShowQueue,
                onTogglePlayMode = {
                    playerViewModel.togglePlayMode()
                    showToast(playerViewModel.currentPlayMode.label)
                },
                onShowLyrics = onNavigateToLyrics,
            )
        }
    }

    val infoSong = songForInfo
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = { songForInfo = null; songInfo = null },
        )
    }
    songForAddToPlaylist?.let { song ->
        SingleSongAddToPlaylistDialog(
            song = song,
            playlists = uiState.playlists,
            onDismiss = { songForAddToPlaylist = null },
            onSelectPlaylist = { playlist ->
                playerViewModel.addSongToPlaylist(playlist.id, song.id)
                showToast("已添加到歌单「${playlist.name}」")
                songForAddToPlaylist = null
            },
            onCreatePlaylist = playerViewModel::createPlaylist,
        )
    }
    songForDelete?.let { song ->
        DeleteSongConfirmDialog(
            song = song,
            onDismiss = { songForDelete = null },
            onConfirm = {
                songForDelete = null
                deleteSongWithToast(song.id)
            },
        )
    }
}

/** 黑胶封面:外圈环纹 + 内圆旋转 + 异步专辑图。 */
@Composable
private fun VinylCover(
    albumArtUri: Uri?,
    rotationDegrees: Float,
) {
    val sizeDp = 280.dp
    Box(
        modifier = Modifier.size(sizeDp),
        contentAlignment = Alignment.Center,
    ) {
        // 外圈黑胶环纹(固定)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.width / 2f
            val rings = 6
            for (i in 0 until rings) {
                val r = outerRadius * (0.55f + 0.07f * i)
                drawCircle(
                    color = Color(0x33FFFFFF),
                    radius = r,
                    center = Offset(outerRadius, outerRadius),
                    style = Stroke(width = 1.5f),
                )
            }
            drawCircle(
                color = Color(0x66FFFFFF),
                radius = outerRadius - 1f,
                center = Offset(outerRadius, outerRadius),
                style = Stroke(width = 1.2f),
            )
        }
        Box(
            modifier = Modifier
                .size(220.dp)
                .rotate(rotationDegrees)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
    }
}

/** 进度环:Canvas 圆弧 + 时间文字。 */
@Composable
private fun VinylProgressRing(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
) {
    val ringSize = 240.dp
    val strokeWidth = 4.dp
    val primary = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier = Modifier.size(ringSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val s = Size(size.width, size.height)
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = s,
                style = stroke,
            )
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset.Zero,
                size = s,
                style = stroke,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatTime(positionMs),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "/ ${formatTime(durationMs)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ControlBar(
    isPlaying: Boolean,
    canControl: Boolean,
    playModeLabel: String,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onTogglePlayMode: () -> Unit,
    onShowLyrics: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onTogglePlayMode, enabled = canControl) {
            Text(
                text = playModeLabel,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
            )
        }
        IconButton(onClick = onPrev, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = canControl,
            modifier = Modifier.size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(36.dp),
            )
        }
        IconButton(onClick = onNext, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onShowLyrics) {
            Text(
                text = "词",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
