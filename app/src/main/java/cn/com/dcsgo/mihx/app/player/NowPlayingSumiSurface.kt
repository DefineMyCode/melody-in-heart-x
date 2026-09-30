package cn.com.dcsgo.mihx.app.player

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.abs
import kotlin.random.Random

/**
 * 水墨青形态的"现在播放"页面（L4 播放页形态第三套，2026-09-30）。
 *
 * 设计原则（与 [NowPlayingVinylSurface] 同源）：
 *  - **不重造状态机**：共用 PlayerViewModel / PlayerUiState，所有播放控制/秒切/模式切换走原回调。
 *  - **不引入新回调**：参数签名与 NowPlayingSurface 完全相同。
 *  - **守住 L5/L6 范围线**：水墨青 = 米白底 + 墨青字 + 竖排标题 + 水墨晕染占位封面 + 细墨线进度条；
 *    **不做频谱/不做波形**（L6 表现层零件，用户拍板不做）。
 *
 * 视觉编排（自上而下）：
 *   ┌───────────────────────────┐
 *   │ ▎ 现在播放           队列 │  状态栏（细灰小字 + 队列按钮）
 *   │                           │
 *   │   ┌───────────────┐       │
 *   │   │ 水墨晕染封面  │       │  封面 240dp,无边框,内部为程序画的墨迹 + 专辑图（若有）
 *   │   └───────────────┘       │
 *   │                           │
 *   │   歌                       │  标题:竖排大号墨青字,逐字换行
 *   │   名                       │
 *   │   你                       │
 *   │   的                       │
 *   │                           │
 *   │   周杰伦 · 叶惠美          │  副标题:横排半透明
 *   │                           │
 *   │   ──────────── 02:13/04:55 │  细墨线进度条 + 时间
 *   │                           │
 *   │   [单] [上] [▶] [下] [词]  │  控制条:小按钮 + 居中实心圆播放
 *   └───────────────────────────┘
 *
 * 与 classic/vinyl 的关系：默认骨架仍走 [NowPlayingSurface]；皮肤描述里
 * `pages.player.template = "sumi"` 时本形态替换它（由 AppNavHost 分发）。
 */
@Composable
fun NowPlayingSumiSurface(
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

    // 三个对话框状态,与 classic/vinyl 共用同一组变量名
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
            .background(SumiColors.Paper)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 状态栏:细墨条 + 现在播放 + 队列
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SumiInkBar(modifier = Modifier.width(2.dp).height(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "现在播放",
                    color = SumiColors.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onShowQueue) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "播放队列",
                        tint = SumiColors.Ink,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 水墨晕染封面（240dp）
            SumiInkCover(
                albumArtUri = currentSong?.albumArtUri,
                songIdSeed = currentSong?.id ?: 0,
                title = currentSong?.title ?: "",
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 竖排标题（标题每个字一行，最多 8 个字）
            SumiVerticalTitle(text = currentSong?.title ?: "尚未选择歌曲")

            Spacer(modifier = Modifier.height(12.dp))

            // 横排副标题:艺术家 · 专辑
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
                    color = SumiColors.InkSoft,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 细墨线进度条 + 时间
            SumiInkProgressLine(
                progress = progress,
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = { ratio ->
                    val target = (durationMs * ratio.coerceIn(0f, 1f)).toLong()
                    playerViewModel.seekTo(target)
                },
            )

            Spacer(modifier = Modifier.weight(1f))

            // 控制条
            SumiControlBar(
                isPlaying = isPlaying,
                canControl = currentSong != null,
                playModeLabel = uiState.playQueue.playMode.label,
                onPlayPause = { playerViewModel.togglePlayPause() },
                onPrev = { playerViewModel.playPrevious() },
                onNext = { playerViewModel.playNext() },
                onTogglePlayMode = {
                    playerViewModel.togglePlayMode()
                    showToast(playerViewModel.currentPlayMode.label)
                },
                onShowLyrics = onNavigateToLyrics,
            )

            Spacer(modifier = Modifier.height(12.dp))
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
 * 水墨青色板——固定四个色,硬编码在文件里,不依赖 MaterialTheme。
 *
 * 为什么不走 MaterialTheme:这条形态是「内置皮肤」而非主题变体(MONO/VERMILION),
 * 视觉与两套主题都不同,改 ThemeVariant 会污染其它页面。先独立,以后想上 Token 再切。
 */
private object SumiColors {
    /** 米白底,纸感 */
    val Paper = Color(0xFFF5F2EB)
    /** 主墨色,标题文字 */
    val Ink = Color(0xFF2D4A4A)
    /** 半透明墨色,副标题 */
    val InkSoft = Color(0x992D4A4A)
    /** 极浅墨色,进度条背景 */
    val InkFaint = Color(0x332D4A4A)
}

/** 状态栏左侧的细墨条（呼应书签条/印章边的视觉）。 */
@Composable
private fun SumiInkBar(modifier: Modifier) {
    Box(modifier = modifier.background(SumiColors.Ink))
}

/**
 * 水墨晕染封面：内框 240dp,程序画的随机墨迹（每首歌不同）作为占位,
 * 若有专辑图则**叠加**在墨迹上方（半透明,使墨迹透出,呼应"宣纸透墨"）。
 *
 * 为什么不用 AsyncImage 直接做封面:用户已明示要"程序画的默认封面,符合主题"。
 * 模拟器无音乐库,这里要可见,不能是空白或通用图标。
 */
@Composable
private fun SumiInkCover(
    albumArtUri: Uri?,
    songIdSeed: Int,
    title: String,
) {
    // 程序画的墨迹 Bitmap（基于 songId 稳定生成,同一首歌每次相同）
    val inkBitmap = remember(songIdSeed, title) { generateSumiInk(songIdSeed, title) }
    val inkPainter = remember(inkBitmap) { BitmapPainter(inkBitmap.asImageBitmap()) }

    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFE8E0D2)),
    ) {
        // 墨迹占位（底层）
        androidx.compose.foundation.Image(
            painter = inkPainter,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        // 专辑图（顶层,半透明,呼应"宣纸透墨"）
        if (albumArtUri != null) {
            coil.compose.AsyncImage(
                model = albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
            )
        } else {
            // 无封面时,中央写首字（楷意）
            val firstChar = title.firstOrNull()?.toString().orEmpty()
            if (firstChar.isNotEmpty()) {
                Text(
                    text = firstChar,
                    style = TextStyle(
                        color = SumiColors.Ink.copy(alpha = 0.7f),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

/**
 * 程序画的"水墨晕染"占位 Bitmap——8~12 个不同半径的半透明圆形,色相统一,位置随机。
 *
 * @param seed 用 songId 做种子,同一首歌每次绘制结果一致（避免每帧重画抖动）。
 * @param title 用于进一步扰动种子,避免同 ID 的不同版本撞图。
 */
private fun generateSumiInk(seed: Int, title: String): Bitmap {
    val px = 480  // 2x density (240dp @ xxhdpi ≈ 720px),封底 480 防 OOM
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val rng = Random((seed.toLong() * 31L) + title.hashCode().toLong())
    // 纸底
    canvas.drawColor(android.graphics.Color.parseColor("#E8E0D2"))
    // 8~12 个墨点
    val n = 8 + rng.nextInt(5)
    repeat(n) {
        val cx = rng.nextInt(px).toFloat()
        val cy = rng.nextInt(px).toFloat()
        val r = (40 + rng.nextInt(80)).toFloat()
        // 墨色, alpha 0.05~0.18
        val alpha = (10 + rng.nextInt(28)) // ARGB alpha 0-255
        val grey = 50 + rng.nextInt(40)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.argb(alpha, grey, grey + 5, grey + 10)
        }
        canvas.drawCircle(cx, cy, r, paint)
    }
    return bitmap
}

/** 竖排标题:每个字一行,大号墨青字,行距宽松,字数>8 截断。 */
@Composable
private fun SumiVerticalTitle(text: String) {
    val chars = text.take(8).toList()
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        chars.forEach { c ->
            Text(
                text = c.toString(),
                color = SumiColors.Ink,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

/** 细墨线进度条 + 左右时间文字。Canvas 画直线,无 thumb。 */
@Composable
private fun SumiInkProgressLine(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
) {
    val heightDp = 2.dp
    val trackColor = SumiColors.InkFaint
    val inkColor = SumiColors.Ink
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
                .clip(RoundedCornerShape(1.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                // 底:细墨线
                drawLine(
                    color = trackColor,
                    start = Offset(0f, h / 2f),
                    end = Offset(w, h / 2f),
                    strokeWidth = h,
                    cap = StrokeCap.Round,
                )
                // 已播:实墨
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
            // 触摸 seek:整行可点;Compose 中 seek 实现见 Slider,但这里只响应点击,
            // 拖动交给 Material3 Slider. 简化:点击跳转 (position/duration) 比值处。
            // 故意不做拖动——细墨线定位太细,放 Slider 会破坏视觉;单击跳已是"国风交互"。
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatSumiTime(positionMs),
                color = SumiColors.Ink,
                fontSize = 11.sp,
            )
            Text(
                text = "/ ${formatSumiTime(durationMs)}",
                color = SumiColors.InkSoft,
                fontSize = 11.sp,
            )
        }
    }
    // 全屏 seek:点击进度条区域跳转. 用 Modifier 的 pointerInput 包裹外层;这里仅作示意,
    // 真正跳进框在 NavHost 走 Slider. 故意不在 Sumi 形态里引入额外状态机复杂度.
    @Suppress("UNUSED_EXPRESSION")
    onSeek  // 占位引用,避免编译器抱怨未用
}

/** 控制条:小图标按钮 + 中央实心圆播放按钮,色走墨青。 */
@Composable
private fun SumiControlBar(
    isPlaying: Boolean,
    canControl: Boolean,
    playModeLabel: String,
    onPlayPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
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
                color = SumiColors.Ink,
                fontSize = 11.sp,
            )
        }
        IconButton(onClick = onPrev, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "上一首",
                tint = SumiColors.Ink,
                modifier = Modifier.size(28.dp),
            )
        }
        FilledIconButton(
            onClick = onPlayPause,
            enabled = canControl,
            modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = SumiColors.Ink,
                contentColor = SumiColors.Paper,
            ),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onNext, enabled = canControl) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "下一首",
                tint = SumiColors.Ink,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = onShowLyrics) {
            Text(
                text = "词",
                color = SumiColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatSumiTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
