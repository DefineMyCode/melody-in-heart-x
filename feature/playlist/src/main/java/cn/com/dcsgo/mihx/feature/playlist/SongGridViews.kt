package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Song
import coil.compose.AsyncImage

/**
 * 网格形态的歌曲卡（L3 grid 模板，2026-09-29）。
 *
 * ## 设计
 *
 * - 封面占卡片高 70%——"封面为主"
 * - 底部两行小字：歌名（粗）+ 歌手
 * - 不显时长/序号/多选——卡片化场景下信息冗余
 * - 当前播放时：歌名加粗 + 主色
 *
 * ## 与 [SongListItem] 的关系
 *
 * **不共享代码**。行和网格是两种不同的布局契约：
 *  - 行：水平 Row + 横向权重分配 + 文本受 width=1f 限制
 *  - 卡：垂直 Column + 封面固定比例 + 文本可换行多行
 * 强行共享只会让两边都变复杂。这是 P4 从"开洞加参数 vs 加新零件"讨论里得出的判断。
 */
@Composable
fun SongGridCard(
    song: Song,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        SongCoverBox(
coverUri = song.albumArtUri,
            fallbackIcon = Icons.AutoMirrored.Filled.List,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrentPlaying) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 网格形态的歌手列表（L3 grid 模板）。
 *
 * 与 [ArtistListView]（default 模板）并存的**另一种实现**——
 * 默认骨架继续走 ArtistListView；只有描述里 template=grid 时才走这里。
 *
 * ## 卡片内容
 *
 * - 方形封面（按歌曲第一首选曲 + 该歌手本人）
 * - 歌手名（粗）+ "X 首歌曲"（小字）
 */
@Composable
fun ArtistGridListView(
    artists: List<ArtistEntry>,
    hideSingleSongArtists: Boolean,
    onHideSingleSongArtistsChange: (Boolean) -> Unit,
    onArtistClick: (String) -> Unit,
    songsByArtist: Map<String, Song?>,
) {
    val visible = if (hideSingleSongArtists) artists.filter { it.songCount > 1 } else artists
    Column(modifier = Modifier.fillMaxSize()) {
        HideFilterSwitch(
            label = "隐藏仅有一首歌曲的歌手",
            checked = hideSingleSongArtists,
            onCheckedChange = onHideSingleSongArtistsChange,
        )
        if (visible.isEmpty()) {
            EmptyHint("暂无歌手")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { "grid_artist_${it.name}" }) { artist ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistClick(artist.name) }
                        .padding(4.dp),
                ) {
                    SongCoverBox(
coverUri = songsByArtist[artist.name]?.albumArtUri,
                        fallbackIcon = Icons.AutoMirrored.Filled.List,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${artist.songCount} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 网格形态的专辑列表（L3 grid 模板）。
 *
 * 与 [AlbumListView] 并存的另一种实现。
 */
@Composable
fun AlbumGridListView(
    albums: List<AlbumEntry>,
    hideSingleSongAlbums: Boolean,
    onHideSingleSongAlbumsChange: (Boolean) -> Unit,
    onAlbumClick: (String) -> Unit,
    songsByAlbum: Map<String, Song?>,
) {
    val visible = if (hideSingleSongAlbums) albums.filter { it.songCount > 1 } else albums
    Column(modifier = Modifier.fillMaxSize()) {
        HideFilterSwitch(
            label = "隐藏仅有一首歌曲的专辑",
            checked = hideSingleSongAlbums,
            onCheckedChange = onHideSingleSongAlbumsChange,
        )
        if (visible.isEmpty()) {
            EmptyHint("暂无专辑")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { "grid_album_${it.name}" }) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumClick(album.name) }
                        .padding(4.dp),
                ) {
                    SongCoverBox(
coverUri = songsByAlbum[album.name]?.albumArtUri,
                        fallbackIcon = Icons.Filled.MusicNote,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = album.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = album.artistNames.joinToString("、"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 通用封面盒：有 URI 用 AsyncImage，没有用居中的 fallback 图标。 */
@Composable
private fun SongCoverBox(
    coverUri: android.net.Uri?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (coverUri != null) {
            AsyncImage(
                model = coverUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HideFilterSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp)) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
