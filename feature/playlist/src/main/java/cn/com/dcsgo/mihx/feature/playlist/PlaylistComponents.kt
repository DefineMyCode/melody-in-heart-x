package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.common.time.formatDurationTime
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.ui.components.EqualizerIndicator
import cn.com.dcsgo.mihx.ui.components.SongListItem

/** 曲库页顶部类目 */
enum class LibraryTab(val label: String) {
    PLAYLISTS("歌单"),
    ARTISTS("歌手"),
    ALBUMS("专辑"),
    EMOTIONS("情绪"),
}

// ─────────────────────────────────────────────────────────────────────────────
// 空状态组件
// ─────────────────────────────────────────────────────────────────────────────

/** 全页空状态：歌曲和歌单都为空 */
@Composable
fun EmptyLibraryHint() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.queue_music_24),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "还没有任何音乐",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "点击右上角「本地音乐」导入本地音乐文件，\n导入后就可以在这里看到啦~",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 歌单详情内空状态 */
@Composable
fun EmptyPlaylistDetailHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "这个歌单还是空的",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "长按任意歌曲即可添加到这个歌单",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
    }
}

/** 区块级小空状态提示 */
@Composable
fun EmptySectionHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌曲列表项（支持添加到歌单 / 添加到队列 / 移除）
// ─────────────────────────────────────────────────────────────────────────────

/** 歌曲条目「更多」菜单动作项（实现迁移至 core:ui 供各 feature 复用） */
typealias SongItemAction = cn.com.dcsgo.mihx.ui.components.SongItemAction

/**
 * 歌曲列表项组件（委托给 core:ui 的共享 [SongListItem]，保留多选指示与更多菜单）
 *
 * @param song              歌曲数据
 * @param isCurrentPlaying  是否正在播放（影响封面和标题样式）
 * @param showDuration      是否展示歌曲时长
 * @param isSelectMode      是否处于多选模式（行首显示选中指示，隐藏「⋯」菜单）
 * @param isSelected        是否已选中（仅多选模式下生效）
 * @param onSongClick       点击回调（多选模式下由调用方传入选中切换逻辑）
 * @param menuActions       更多操作菜单项（非空则在条目末尾显示「⋯」菜单）
 */
@Composable
fun SongItem(
    song: Song,
    isCurrentPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    showDuration: Boolean = false,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSongClick: (Song) -> Unit,
    menuActions: List<SongItemAction> = emptyList(),
    subline: (@Composable () -> Unit)? = null,
) {
    SongListItem(
        song = song,
        isCurrentPlaying = isCurrentPlaying,
        modifier = modifier,
        showDuration = showDuration,
        onClick = { onSongClick(song) },
        subline = subline,
        leading = if (isSelectMode) {
            {
                // 多选模式：行首选中指示（与 LocalSongItem 一致）
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已选中",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AddCircleOutline,
                            contentDescription = "未选中",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
        } else {
            null
        },
        trailing = if (!isSelectMode && menuActions.isNotEmpty()) {
            { SongItemMenu(menuActions) }
        } else {
            null
        },
    )
}

/** 歌曲条目「更多」下拉菜单（多选模式隐藏） */
@Composable
private fun SongItemMenu(menuActions: List<SongItemAction>) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            menuActions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action.destructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        action.onClick()
                    },
                    leadingIcon = action.leadingIcon,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌单列表项（带更多操作菜单）
// ─────────────────────────────────────────────────────────────────────────────

/**
 * 歌单列表项组件
 *
 * 显示歌单封面（取第一首歌的封面）、名称、歌曲数量，以及重命名/删除操作菜单。
 *
 * @param playlist         歌单数据
 * @param songs            所有歌曲列表（用于获取封面）
 * @param songById         歌曲 ID → 歌曲 映射（由父层 remember 一次，避免每个歌单项 O(M) 扫描）
 * @param onPlaylistClick  点击歌单的回调
 * @param onDelete         删除歌单的回调
 * @param onRename         重命名歌单的回调
 */
@Composable
fun PlaylistItem(
    playlist: Playlist,
    songs: List<Song> = emptyList(),
    songById: Map<Int, Song> = emptyMap(),
    onPlaylistClick: (Playlist) -> Unit,
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {},
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlaylistClick(playlist) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            val firstSong = remember(playlist.songIds, songById) {
                playlist.songIds.firstNotNullOfOrNull { songById[it] }
            }
            if (firstSong?.albumArtUri != null) {
                AsyncImage(
                    model = firstSong.albumArtUri,
                    contentDescription = "专辑封面",
                    modifier = Modifier.size(64.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (playlist.songCount == 0) "暂无歌曲" else "${playlist.songCount} 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "更多")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        showMenu = false
                        onRename()
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Create, contentDescription = null)
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除歌单", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌手列表项
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ArtistItem(
    artistName: String,
    songCount: Int,
    albumCount: Int,
    coverUri: android.net.Uri?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCoverThumb(
            uri = coverUri,
            size = 64,
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artistName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = "$albumCount 张专辑 · $songCount 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 专辑列表项
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlbumItem(
    albumName: String,
    artistName: String,
    songCount: Int,
    coverUri: android.net.Uri?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryCoverThumb(
            uri = coverUri,
            size = 64,
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = albumName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = "$artistName · $songCount 首歌曲",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 通用封面缩略图（无封面时显示默认图标）
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun LibraryCoverThumb(
    uri: android.net.Uri?,
    size: Int,
    shape: RoundedCornerShape,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier.size(size.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size((size * 0.45).dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌单续播横幅
// ─────────────────────────────────────────────────────────────────────────────

/** 歌单详情页顶部续播横幅：点击从记录歌曲开头开始播放，× 关闭 */
@Composable
fun PlaylistResumeBanner(
    resumeSong: Song,
    onResumePlaylist: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onResumePlaylist)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "继续播放 · 从《${resumeSong.title}》开始",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (resumeSong.artist.isNotBlank()) {
                    Text(
                        text = resumeSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭续播横幅",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
