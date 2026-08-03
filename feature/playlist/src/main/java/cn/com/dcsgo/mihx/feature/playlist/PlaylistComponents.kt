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
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

/** 曲库页顶部类目 */
enum class LibraryTab(val label: String) {
    PLAYLISTS("歌单"),
    ARTISTS("歌手"),
    ALBUMS("专辑"),
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

/**
 * 歌曲列表项组件
 *
 * @param song              歌曲数据
 * @param isCurrentPlaying  是否正在播放（影响封面和标题样式）
 * @param onSongClick       点击回调
 * @param onShowAddToPlaylist 添加到歌单回调（非空则显示对应按钮）
 * @param showRemoveButton  是否显示移除按钮（歌单详情页）
 * @param onRemoveClick     移除按钮点击回调
 * @param onAddToQueue      加入播放队列回调（非空则显示对应按钮）
 * @param onAddToNextPlay   加入下一首播放回调（非空则显示对应按钮）
 */
@Composable
fun SongItem(
    song: Song,
    isCurrentPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    onSongClick: (Song) -> Unit,
    onShowAddToPlaylist: ((Song) -> Unit)? = null,
    showRemoveButton: Boolean = false,
    onRemoveClick: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToNextPlay: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSongClick(song) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 封面 / 播放指示
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
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
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    painter = painterResource(
                        id = if (isCurrentPlaying) R.drawable.pause_24 else R.drawable.queue_music_24
                    ),
                    contentDescription = if (isCurrentPlaying) "正在播放" else "播放",
                    modifier = Modifier.size(20.dp),
                    tint = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        // 移除按钮（歌单详情页显示）
        if (showRemoveButton && onRemoveClick != null) {
            IconButton(onClick = onRemoveClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "从歌单移除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 加入播放队列按钮
        if (onAddToQueue != null) {
            IconButton(onClick = onAddToQueue) {
                Icon(
                    painter = painterResource(id = R.drawable.music_note_add_24),
                    contentDescription = "加入播放队列",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 下一首播放按钮
        if (onAddToNextPlay != null) {
            IconButton(onClick = onAddToNextPlay) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_next),
                    contentDescription = "下一首播放",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // 添加到歌单按钮
        if (onShowAddToPlaylist != null) {
            IconButton(onClick = { onShowAddToPlaylist(song) }) {
                Icon(
                    painter = painterResource(id = R.drawable.list_alt_add_24),
                    contentDescription = "添加到歌单",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
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
 * @param onPlaylistClick  点击歌单的回调
 * @param onDelete         删除歌单的回调
 * @param onRename         重命名歌单的回调
 */
@Composable
fun PlaylistItem(
    playlist: Playlist,
    songs: List<Song> = emptyList(),
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
            val firstSong = songs.firstOrNull { playlist.songIds.contains(it.id) }
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
