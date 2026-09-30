package cn.com.dcsgo.mihx.app.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.app.playlist.PlaylistResumeViewModel
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.feature.player.PlayerUiState
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.DeleteSongConfirmDialog
import cn.com.dcsgo.mihx.ui.components.SingleSongAddToPlaylistDialog
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import coil.compose.AsyncImage

/**
 * 网易云式播放页（L4 播放页形态第四套，2026-09-30）。
 *
 * 设计目标：**近似观感**，不是像素级还原。
 *
 * 模仿要素（来自 2026 网易云音乐播放页真机截屏）：
 *  - 黑底 `#000000` + 白字 + 红 CTA 心形
 *  - 大封面居中（占屏 70%，圆角 12dp），**背景从封面上下边缘取色做三段渐变**（顶/底/深）
 *  - 顶栏左下"↓"收起 + 右上"•••"更多
 *  - 歌名居中大字 18sp + 副标(歌手/专辑)居中小字 12sp
 *  - 喜欢 ❤️ + 喜欢数（5258 这种格式）
 *  - 底部 5 控件：上一首 / 播放 / 下一首 / 队列 / 更多（最右"更多"打开歌词页）
 *
 * 范围线（L4 + L5/L6）：
 *  - 不做频谱/不做波形
 *  - 不做主题扩展（仍走 MONO.dark + 自己覆盖底色为 `#000000`）
 *  - 取色算法走 Android 提供的 `Bitmap.getPixel`（不引入图像处理库）
 *
 * 与 classic/vinyl/sumi 同源设计：
 *  - 共用 PlayerViewModel / UiState，不重造状态机
 *  - 参数签名与 NowPlayingSurface 完全相同
 */
@Composable
fun NowPlayingNeteaseSurface(
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

    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 取色：从专辑图上下边缘各取一段平均色，用于背景渐变
    // 用 songId 做 key,只在新歌时重取(切歌/首次进入)
    val palette = remember(currentSong?.id, currentSong?.albumArtUri) {
        SamplePalette(top = NeteasePalette.Black, bottom = NeteasePalette.Black, accent = NeteasePalette.Red)
    }

    // 三个对话框,与经典同款变量名
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.top.copy(alpha = 0.7f),
                        NeteasePalette.Black,
                        palette.bottom.copy(alpha = 0.7f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // 顶栏:左下收起 + 右上更多
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onShowQueue) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "收起",
                        tint = NeteasePalette.OnSurface,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    currentSong?.let { songForInfo = it; songInfo = null }
                }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "更多",
                        tint = NeteasePalette.OnSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.4f))

            // 大封面 280dp + 取色 + 占位
            NeteaseCover(
                albumArtUri = currentSong?.albumArtUri,
                onPaletteExtracted = { p ->
                    // 不更新 state(remember 已锁) — 仅日志,后续可接入
                },
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 歌名居中
            Text(
                text = currentSong?.title ?: "尚未选择歌曲",
                color = NeteasePalette.OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 副标(歌手/专辑)居中
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
                    color = NeteasePalette.OnSurfaceSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 喜欢 ❤️ + 喜欢数(模仿网易云的"5258"格式)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "喜欢",
                    tint = NeteasePalette.Red,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "5258",
                    color = NeteasePalette.OnSurfaceSoft,
                    fontSize = 11.sp,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "VIP · 网易云音乐",
                    color = NeteasePalette.OnSurfaceSoft,
                    fontSize = 10.sp,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 进度条 + 时间
            NeteaseProgressLine(progress = progress, positionMs = positionMs, durationMs = durationMs)

            Spacer(modifier = Modifier.weight(1f))

            // 底部 5 控件:上一首 / 播放 / 下一首 / 队列 / 更多(打开歌词)
            NeteaseControlBar(
                isPlaying = isPlaying,
                canControl = currentSong != null,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onPrev = { playerViewModel.playPrevious() },
                onNext = { playerViewModel.playNext() },
                onQueue = onShowQueue,
                onShowLyrics = onNavigateToLyrics,
            )

            Spacer(modifier = Modifier.height(8.dp))
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

/**
 * 网易云式色板（与 ThemeVariant 解耦的硬编码色）。
 *
 * 与 [NowPlayingSumiSurface.SumiColors] 同套路：硬编码而非走 MaterialTheme，
 * 因为网易云色 (#000000 + #C20C0C) 与 MONO/VERMILION 都不同，会污染其它页面。
 */
private object NeteasePalette {
    val Black = Color(0xFF000000)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceSoft = Color(0xB3FFFFFF) // 70% 白
    val Red = Color(0xFFC20C0C) // 网易云品牌红
    val RedSoft = Color(0x33C20C0C)
}

/** 取色结果(top / bottom / accent),当前未启用实际取色逻辑,保留接口。 */
private data class SamplePalette(val top: Color, val bottom: Color, val accent: Color)

/** 大封面 280dp,圆角 12dp。无封面时显示红心占位。 */
@Composable
private fun NeteaseCover(
    albumArtUri: Uri?,
    onPaletteExtracted: (SamplePalette) -> Unit,
) {
    Box(
        modifier = Modifier
            .size(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NeteasePalette.Black),
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
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = NeteasePalette.Red,
                modifier = Modifier.size(80.dp),
            )
        }
        // 回调占位(下次接真取色时填),目前保留接口稳定
        onPaletteExtracted(SamplePalette(NeteasePalette.Black, NeteasePalette.Black, NeteasePalette.Red))
    }
}

/** 进度条 + 时间文字,网易云式:细白线 + 圆形 thumb + 时间贴边。 */
@Composable
private fun NeteaseProgressLine(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
) {
    val heightDp = 2.dp
    val inkColor = NeteasePalette.OnSurface
    val trackColor = NeteasePalette.OnSurfaceSoft.copy(alpha = 0.3f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .clip(RoundedCornerShape(1.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = trackColor,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = h,
                    cap = StrokeCap.Round,
                )
                if (progress > 0f) {
                    drawLine(
                        color = inkColor,
                        start = Offset(0f, h / 2f),
                        end = Offset(w * progress, h / 2f),
                        strokeWidth = h,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatNeteaseTime(positionMs),
                color = NeteasePalette.OnSurfaceSoft,
                fontSize = 10.sp,
            )
            Text(
                text = formatNeteaseTime(durationMs),
                color = NeteasePalette.OnSurfaceSoft,
                fontSize = 10.sp,
            )
        }
    }
}

/** 5 控件:上一首 / 播放 / 下一首 / 队列 / 更多。中间播放为大圆白底。 */
@Composable
private fun NeteaseControlBar(
    isPlaying: Boolean,
    canControl: Boolean,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onQueue: () -> Unit,
    onShowLyrics: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onQueue) {
            Icon(
                imageVector = Icons.Filled.QueueMusic,
                contentDescription = "播放队列",
                tint = NeteasePalette.OnSurface,
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onPrev, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "上一首",
                tint = NeteasePalette.OnSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        androidx.compose.material3.FilledIconButton(
            onClick = onPlayPause,
            enabled = canControl,
            modifier = Modifier.size(64.dp),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = NeteasePalette.OnSurface,
                contentColor = NeteasePalette.Black,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onNext, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "下一首",
                tint = NeteasePalette.OnSurface,
                modifier = Modifier.size(32.dp),
            )
        }
        IconButton(onClick = onShowLyrics) {
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = "歌词",
                tint = NeteasePalette.OnSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun formatNeteaseTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
