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
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.core.model.LibraryCatalog
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 专辑详情页
 *
 * 展示两部分内容：
 * 1. 该专辑关联的歌曲
 * 2. 该专辑歌手关联的其他专辑（与专辑共享任一歌手的其他专辑）
 */
@Composable
fun AlbumDetailScreen(
    albumName: String,
    songs: List<Song>,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit = {},
) {
    // 该专辑的所有歌曲（不限定单个歌手）
    val albumSongs = songs.filter { it.album == albumName }
    val totalDurationMs = albumSongs.sumOf { it.durationMs }
    // 该专辑关联的所有歌手（拆分后的最小单位）
    val albumArtists = albumSongs.flatMap { it.parsedArtists }.distinct()
    // 与专辑共享任一歌手的其他专辑
    val otherAlbums = LibraryCatalog.deriveAlbums(songs)
        .filter { it.name != albumName && it.artistNames.any { artist -> artist in albumArtists } }
    var selectedSection by rememberSaveable { mutableStateOf(0) }

    // 根容器必须不透明：返回过渡期间退出页绘制在目标页之上，透明会让目标页透出造成两页叠加
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = albumArtists.joinToString("、"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "共 ${formatHoursMinutes(totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            showDuration = true,
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
                    items(otherAlbums, key = { "album_other_${it.name}" }) { album ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAlbumClick(album.name) }
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
    val songs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class AlbumDetailRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onAlbumClick: (String) -> Unit = {},
)

@Composable
fun AlbumDetailRoute(
    state: AlbumDetailRouteState,
    actions: AlbumDetailRouteActions,
) {
    AlbumDetailScreen(
        albumName = state.albumName,
        songs = state.songs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onBack = actions.onBack,
        onSongClick = { song ->
            val context = state.songs.filter { it.album == state.albumName }
            actions.onSongClick(song, context)
        },
        onAlbumClick = actions.onAlbumClick,
    )
}
