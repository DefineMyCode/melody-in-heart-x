package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────
// 文件管理区域
// ─────────────────────────────────────────────────────────────────

@Composable
fun FileManagementSection(
    isImporting: Boolean = false,
    importProgress: Int = 0,
    importTotal: Int = 0,
    onAddFolderClick: () -> Unit,
    onVersionManagementClick: () -> Unit = {},
    onQuickSkipSongsClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "文件管理",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (isImporting) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (importTotal > 0) importProgress.toFloat() / importTotal else 0f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 150,
                            easing = androidx.compose.animation.core.LinearEasing
                        ),
                        label = "progress"
                    )
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (importTotal > 0) "正在导入 $importProgress / $importTotal ..." else "正在扫描文件夹 ...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FileManagementButton(
                    icon = R.drawable.music_note_add_24,
                    label = "添加文件夹",
                    description = "批量导入",
                    onClick = onAddFolderClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )

                FileManagementButton(
                    icon = R.drawable.text_compare_24,
                    label = "多版本管理",
                    description = "查看与修复",
                    onClick = onVersionManagementClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )

                FileManagementButton(
                    icon = R.drawable.skip_next_24,
                    label = "秒切歌曲",
                    description = "记录秒切的歌曲",
                    onClick = onQuickSkipSongsClick,
                    enabled = !isImporting,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun FileManagementButton(
    icon: Int,
    label: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { if (enabled) onClick() }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 本地歌曲列表项（支持多选模式）
// ─────────────────────────────────────────────────────────────────

@Composable
fun LocalSongItem(
    song: Song,
    isCurrentPlaying: Boolean = false,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onShowInfo: (Song) -> Unit = {},
    onAddToPlaylist: (Song) -> Unit = {},
    onDelete: (Song) -> Unit = {},
) {
    val cornerShape = remember { RoundedCornerShape(8.dp) }
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "已选中",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "未选中",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        SongAlbumArt(
            song = song,
            isCurrentPlaying = isCurrentPlaying,
            cornerShape = cornerShape
        )

        Spacer(modifier = Modifier.width(12.dp))

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

        if (!isSelectMode) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("歌曲信息") },
                        onClick = {
                            showMenu = false
                            onShowInfo(song)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("添加到歌单") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylist(song)
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.list_alt_add_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                "删除",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete(song)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 封面图组件
// ─────────────────────────────────────────────────────────────────

@Composable
fun SongAlbumArt(
    song: Song,
    isCurrentPlaying: Boolean,
    cornerShape: RoundedCornerShape,
    size: Int = 48
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(cornerShape)
            .background(
                if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            val iconSize = (size * 0.46).toInt()
            if (isCurrentPlaying) {
                Icon(
                    painter = painterResource(id = R.drawable.pause_24),
                    contentDescription = "正在播放",
                    modifier = Modifier.size(iconSize.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 底部操作栏（添加到歌单 / 清除选择）
// ─────────────────────────────────────────────────────────────────

@Composable
fun SurfaceBar(
    selectedCount: Int,
    onAddToPlaylist: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已选 $selectedCount 首",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear) {
                Text("取消")
            }
            FilledTonalButton(onClick = onAddToPlaylist) {
                Icon(
                    painter = painterResource(R.drawable.list_alt_add_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加到歌单")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 空状态提示
// ─────────────────────────────────────────────────────────────────

@Composable
fun EmptyMusicHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有本地音乐",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击「添加文件夹」导入本地音乐",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 本地音乐标题栏（搜索 / 多选操作）
// ─────────────────────────────────────────────────────────────────

@Composable
fun LocalMusicHeader(
    localSongs: List<Song>,
    displaySongs: List<Song>,
    isSearching: Boolean,
    isSelectMode: Boolean,
    isAllSelected: Boolean,
    selectedCount: Int,
    onToggleSearch: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "本地音乐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSearching)
                    "${displaySongs.size} / ${localSongs.size}"
                else "${localSongs.size} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))

            if (localSongs.isNotEmpty()) {
                IconButton(
                    onClick = onToggleSelectMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.list_alt_add_24),
                        contentDescription = if (isSelectMode) "取消多选" else "多选",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelectMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearching) "关闭搜索" else "搜索",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isSelectMode) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAllSelected) "取消全选" else "全选",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "已选 $selectedCount 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 搜索空结果提示
// ─────────────────────────────────────────────────────────────────

@Composable
fun SearchEmptyHint(searchQuery: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 400.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "未找到匹配「$searchQuery」的歌曲",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
