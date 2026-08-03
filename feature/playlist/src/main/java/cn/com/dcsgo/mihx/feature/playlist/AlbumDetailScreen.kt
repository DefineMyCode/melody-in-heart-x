package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 专辑详情页
 *
 * 展示两部分内容：
 * 1. 该专辑关联的歌曲
 * 2. 该专辑歌手关联的其他专辑
 */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    artistName: String,
    songs: List<Song>,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String, String) -> Unit = { _, _ -> },
) {
    val albumSongs = songs.filter { it.album == albumName && it.artist == artistName }
    // 该歌手的其他专辑
    val otherAlbums = deriveAlbums(songs)
        .filter { it.artistName == artistName && it.name != albumName }
    var selectedSection by rememberSaveable { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 分段导航：歌曲 / 其他专辑
        SegmentedTabRow(
            labels = listOf("歌曲", "其他专辑"),
            selectedIndex = selectedSection,
            onTabSelected = { selectedSection = it },
        )

        if (selectedSection == 0) {
            // ── 歌曲 ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (albumSongs.isEmpty()) {
                    item(key = "album_songs_empty") {
                        EmptySectionHint("该专辑暂无歌曲")
                    }
                } else {
                    items(albumSongs, key = { "album_song_${it.id}" }) { song ->
                        SongItem(
                            song = song,
                            isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                            onSongClick = onSongClick,
                        )
                    }
                }
            }
        } else {
            // ── 该歌手的其他专辑 ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (otherAlbums.isEmpty()) {
                    item(key = "album_other_empty") {
                        EmptySectionHint("该歌手暂无其他专辑")
                    }
                } else {
                    items(otherAlbums, key = { "album_other_${it.artistName}_${it.name}" }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAlbumClick(album.name, album.artistName) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LibraryCoverThumb(
                                uri = album.coverUri,
                                size = 48,
                                shape = RoundedCornerShape(6.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${album.songCount} 首歌曲",
                                    style = MaterialTheme.typography.bodySmall,
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
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 专辑详情 Route
// ─────────────────────────────────────────────────────────────────────────────

data class AlbumDetailRouteState(
    val albumName: String,
    val artistName: String,
    val songs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class AlbumDetailRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onAlbumClick: (String, String) -> Unit = { _, _ -> },
)

@Composable
fun AlbumDetailRoute(
    state: AlbumDetailRouteState,
    actions: AlbumDetailRouteActions,
) {
    AlbumDetailScreen(
        albumName = state.albumName,
        artistName = state.artistName,
        songs = state.songs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onBack = actions.onBack,
        onSongClick = { song ->
            val context = state.songs.filter {
                it.album == state.albumName && it.artist == state.artistName
            }
            actions.onSongClick(song, context)
        },
        onAlbumClick = actions.onAlbumClick,
    )
}
