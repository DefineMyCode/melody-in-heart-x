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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 歌手详情页
 *
 * 展示两部分内容：
 * 1. 该歌手关联的专辑
 * 2. 该歌手关联的歌曲
 */
@Composable
fun ArtistDetailScreen(
    artistName: String,
    songs: List<Song>,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit = {},
) {
    // 该歌手的所有歌曲（歌手为拆分后的最小单位，歌曲属于任一拆分歌手即关联）
    val artistSongs = songs.filter { artistName in it.parsedArtists }
    val albums = deriveAlbums(artistSongs)
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
                    text = artistName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${albums.size} 张专辑 · ${artistSongs.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 分段导航：歌曲 / 专辑
        SegmentedTabRow(
            labels = listOf("歌曲", "专辑"),
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
                if (artistSongs.isEmpty()) {
                    item(key = "artist_songs_empty") {
                        EmptySectionHint("该歌手暂无歌曲")
                    }
                } else {
                    items(artistSongs, key = { "artist_song_${it.id}" }) { song ->
                        SongItem(
                            song = song,
                            isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                            onSongClick = onSongClick,
                        )
                    }
                }
            }
        } else {
            // ── 专辑 ──
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (albums.isEmpty()) {
                    item(key = "artist_albums_empty") {
                        EmptySectionHint("该歌手暂无专辑")
                    }
                } else {
                    items(albums, key = { "artist_album_${it.name}" }) { album ->
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
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
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
// 歌手详情 Route
// ─────────────────────────────────────────────────────────────────────────────

data class ArtistDetailRouteState(
    val artistName: String,
    val songs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class ArtistDetailRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onAlbumClick: (String) -> Unit = {},
)

@Composable
fun ArtistDetailRoute(
    state: ArtistDetailRouteState,
    actions: ArtistDetailRouteActions,
) {
    ArtistDetailScreen(
        artistName = state.artistName,
        songs = state.songs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onBack = actions.onBack,
        onSongClick = { song ->
            val context = state.songs.filter { state.artistName in it.parsedArtists }
            actions.onSongClick(song, context)
        },
        onAlbumClick = actions.onAlbumClick,
    )
}
