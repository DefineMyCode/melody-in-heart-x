package cn.com.dcsgo.mihx.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.launch

/**
 * 播放队列底部抽屉面板
 *
 * 显示当前播放队列中的所有歌曲，支持：
 * - 点击歌曲跳转播放
 * - 滑动删除/移除歌曲（可选）
 * - 显示当前播放歌曲高亮
 *
 * @param playQueue          当前播放队列
 * @param isShown            是否显示
 * @param currentSongId      当前播放歌曲的 ID（用于高亮）
 * @param onSongClick        点击队列中的歌曲回调
 * @param onRemoveSong       移除歌曲回调，参数是队列索引（可选）
 * @param onClearQueue       清空队列回调（可选）
 * @param onDismiss          关闭抽屉回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayQueueSheet(
    playQueue: PlayQueue,
    isShown: Boolean,
    currentSongId: Int? = null,
    onSongClick: (Int) -> Unit = {},      // 参数是索引
    onRemoveSong: (Int) -> Unit = {},     // 参数是队列索引
    onClearQueue: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    if (!isShown) return

    var showClearConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState()
    val currentIndex = playQueue.currentIndex

    // 一键跳转到当前播放歌曲：先完全展开抽屉，再滚动到目标项
    fun scrollToCurrentSong() {
        if (currentIndex >= 0 && playQueue.songs.isNotEmpty()) {
            coroutineScope.launch {
                sheetState.expand()
                listState.animateScrollToItem(currentIndex.coerceAtMost(playQueue.songs.lastIndex))
            }
        }
    }

    // 清空确认弹窗
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空播放队列") },
            text = { Text("确定要清空当前播放队列吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    onClearQueue()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "播放队列 (${playQueue.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 一键定位到当前播放歌曲（队列非空且有播放索引时显示）
                    if (!playQueue.isEmpty && currentIndex >= 0) {
                        IconButton(onClick = { scrollToCurrentSong() }) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "定位到当前播放",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 清空按钮（队列非空时显示）
                    if (!playQueue.isEmpty) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "清空队列",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "关闭")
                    }
                }
            }

            HorizontalDivider()

            // 队列为空提示
            if (playQueue.isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "队列为空",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 队列列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    itemsIndexed(playQueue.songs, key = { _, song -> song.id }) { index, song ->
                        val isCurrentPlaying = playQueue.currentIndex == index && currentSongId == song.id
                        QueueSongItem(
                            song = song,
                            index = index,
                            isCurrentPlaying = isCurrentPlaying,
                            onItemClick = { onSongClick(index) },
                            onRemoveClick = { onRemoveSong(index) }
                        )
                    }
                }
            }

            // 底部间距
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 播放队列中的单首歌曲项
 */
@Composable
private fun QueueSongItem(
    song: Song,
    index: Int,
    isCurrentPlaying: Boolean = false,
    onItemClick: () -> Unit = {},
    onRemoveClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onItemClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 序号或播放图标
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrentPlaying) {
                Icon(
                    painter = painterResource(id = R.drawable.pause_24),
                    contentDescription = "正在播放",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(
                    text = (index + 1).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 移除按钮
        IconButton(
            onClick = onRemoveClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "从队列移除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
