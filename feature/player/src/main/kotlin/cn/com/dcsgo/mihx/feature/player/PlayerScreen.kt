@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.SeekSlider
import cn.com.dcsgo.mihx.feature.player.component.QueuePanel
import coil.compose.AsyncImage

@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onOpenLyrics: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // 500ms progress tick; collected while the screen is at least STARTED (plan P1-7).
    val tick by viewModel.progressFlow().collectAsStateWithLifecycle(0L)

    // P5-A: the library is loaded from Room (populated by the SAF import pipeline), so no media-read
    // permission gate is required here. The home screen prompts the user to import when empty.
    LaunchedEffect(Unit) {
        viewModel.loadLibrary()
    }

    var showQueue by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // 封面点击节流：快速连点只响应一次，避免第二下落在导航过渡中已渲染的歌词页
    // 歌词行上（那是误触 seek 的来源）。
    var lastCoverClick by remember { mutableStateOf(0L) }
    val openLyricsThrottled = {
        val now = System.currentTimeMillis()
        if (now - lastCoverClick >= COVER_CLICK_THROTTLE_MS) {
            lastCoverClick = now
            onOpenLyrics()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val currentSong = remember(state.queue, state.highlightIndex) {
                state.queue.orderedSongs().getOrNull(state.highlightIndex)
            }
            Box(modifier = Modifier.size(280.dp)) {
                AsyncImage(
                    model = currentSong?.albumArtUri,
                    contentDescription = "点击查看歌词",
                    // UI 设计定稿：大封面 280dp + 16dp 圆角；整张封面就是歌词入口（替代原 FAB）。
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { openLyricsThrottled() },
                    contentScale = ContentScale.Crop,
                )
                // 底部"点击查看歌词"提示 pill，让封面的可点击语义显式可见。
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "点击查看歌词",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentSong?.title?.ifBlank { "未知标题" } ?: "无曲目",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = currentSong?.artist?.ifBlank { "未知艺人" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SeekSlider(
                value = if (state.isDragging) state.sliderPositionMs.toFloat() else tick.toFloat(),
                valueRange = 0f..(if (state.durationMs > 0) state.durationMs.toFloat() else 1f),
                onValueChange = { viewModel.onSeekDrag(it.toLong()) },
                onValueChangeFinished = viewModel::onSeekDragEnded,
                modifier = Modifier.fillMaxWidth(),
            )
            // 时间标签：拖拽中显示拖动位置，否则显示实时进度（UI 设计定稿 labelSmall）。
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlaybackTime(if (state.isDragging) state.sliderPositionMs else tick),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatPlaybackTime(state.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::onPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一首",
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                // 播放/暂停是主操作：按钮与图标都显著大于其他控制（UI 设计定稿）。
                IconButton(
                    onClick = viewModel::onPlayPause,
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(48.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                IconButton(onClick = viewModel::onNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showQueue = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "播放队列")
        }
    }

    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = sheetState,
        ) {
            val ordered = remember(state.queue) { state.queue.orderedSongs() }
            QueuePanel(
                songs = ordered,
                currentIndex = state.highlightIndex,
                playMode = state.queue.playMode,
                onItemClick = { viewModel.onJumpTo(it) },
                onRemoveClick = { viewModel.onRemoveAt(it) },
                onModeChange = { viewModel.onSwitchMode(it) },
            )
        }
    }
}

private fun formatPlaybackTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/** Debounce window for the cover-to-lyrics entry (rapid double-taps are dropped). */
private const val COVER_CLICK_THROTTLE_MS: Long = 600
