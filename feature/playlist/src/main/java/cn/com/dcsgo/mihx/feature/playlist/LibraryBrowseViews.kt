package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/** 单个歌手聚合数据 */
data class ArtistEntry(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val coverUri: android.net.Uri?,
)

/** 单个专辑聚合数据 */
data class AlbumEntry(
    val name: String,
    val artistName: String,
    val songCount: Int,
    val coverUri: android.net.Uri?,
)

/** 从歌曲列表聚合出歌手列表（按歌手名排序） */
fun deriveArtists(songs: List<Song>): List<ArtistEntry> {
    return songs
        .groupBy { it.artist }
        .map { (name, list) ->
            ArtistEntry(
                name = name,
                songCount = list.size,
                albumCount = list.map { it.album }.filter { it.isNotBlank() }.distinct().size,
                coverUri = list.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.name }
}

/** 从歌曲列表聚合出专辑列表（按专辑名排序，跳过空专辑名） */
fun deriveAlbums(songs: List<Song>): List<AlbumEntry> {
    return songs
        .filter { it.album.isNotBlank() }
        .groupBy { it.album to it.artist }
        .map { (key, list) ->
            AlbumEntry(
                name = key.first,
                artistName = key.second,
                songCount = list.size,
                coverUri = list.firstNotNullOfOrNull { it.albumArtUri },
            )
        }
        .sortedBy { it.name }
}

/** 曲库顶部类目切换栏 */
@Composable
fun LibraryTabRow(
    selectedTab: LibraryTab,
    onTabSelected: (LibraryTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val container = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val content = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(container)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = content,
                    )
                }
            }
        }
    }
}

/** 曲库搜索框 */
@Composable
fun LibrarySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "搜索歌单、歌手或专辑",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "清除搜索",
                    )
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** 通用分段式导航栏（用于详情页内专辑/歌曲切换等） */
@Composable
fun SegmentedTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val container = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val content = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(index) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(container)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = content,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 歌手列表页 */
@Composable
fun ArtistListView(
    artists: List<ArtistEntry>,
    onArtistClick: (String) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptySectionHint("暂无歌手")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "artist_header") {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "歌手 (${artists.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(artists, key = { "artist_${it.name}" }) { artist ->
                ArtistItem(
                    artistName = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
                    coverUri = artist.coverUri,
                    onClick = { onArtistClick(artist.name) },
                )
            }
        }
    }
}

/** 专辑列表页 */
@Composable
fun AlbumListView(
    albums: List<AlbumEntry>,
    onAlbumClick: (String, String) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptySectionHint("暂无专辑")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(key = "album_header") {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "专辑 (${albums.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(albums, key = { "album_${it.artistName}_${it.name}" }) { album ->
                AlbumItem(
                    albumName = album.name,
                    artistName = album.artistName,
                    songCount = album.songCount,
                    coverUri = album.coverUri,
                    onClick = { onAlbumClick(album.name, album.artistName) },
                )
            }
        }
    }
}
