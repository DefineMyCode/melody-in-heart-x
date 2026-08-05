package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.core.model.LibraryCatalog
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog

/**
 * 歌手详情页
 *
 * 展示两部分内容：
 * 1. 该歌手关联的专辑
 * 2. 该歌手关联的歌曲（支持多选 / 搜索 / 添加到歌单）
 */
@Composable
fun ArtistDetailScreen(
    artistName: String,
    songs: List<Song>,
    playlists: List<Playlist> = emptyList(),
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onAlbumClick: (String) -> Unit = {},
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
    onAddSongToPlaylist: (Song, Playlist) -> Unit = { _, _ -> },
    onAddSongsToPlaylist: (List<Song>, Playlist) -> Unit = { _, _ -> },
    onCreatePlaylistWithResult: (String) -> Playlist? = { null },
) {
    // 该歌手的所有歌曲（歌手为拆分后的最小单位，歌曲属于任一拆分歌手即关联）：
    // 全库过滤与派生只在曲库/歌手名变化时重算
    val artistSongs = remember(songs, artistName) { songs.filter { artistName in it.parsedArtists } }
    val albums = remember(artistSongs) { LibraryCatalog.deriveAlbums(artistSongs) }
    val totalDurationMs = remember(artistSongs) { artistSongs.sumOf { it.durationMs } }
    var selectedSection by rememberSaveable { mutableStateOf(0) }

    // 多选 / 搜索状态
    val selection = rememberSongSelectionController()
    val displaySongs = selection.filterSongs(artistSongs)
    val selectedSongs = selection.selectedSongs(displaySongs)
    val isAllSelected = selection.isAllSelected(displaySongs)

    // ── Dialog 状态 ──
    var showBatchDialog by remember { mutableStateOf(false) }
    var songForSinglePlaylist by remember { mutableStateOf<Song?>(null) }

    // 歌曲信息 Dialog：选定歌曲后异步加载元数据
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    LaunchedEffect(songForInfo) {
        val uri = songForInfo?.uri
        if (uri != null) {
            songInfo = songForInfo?.let { loadSongInfo(it) }
        }
    }

    // 多选模式下系统返回键先退出多选
    BackHandler(enabled = selection.isSelectMode) {
        selection.exitSelectMode()
    }

    // 根容器必须不透明：返回过渡期间退出页绘制在目标页之上，透明会让目标页透出造成两页叠加
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${albums.size} 张专辑 · ${artistSongs.size} 首歌曲 · 共 ${formatHoursMinutes(totalDurationMs)}",
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
                Column(modifier = Modifier.fillMaxSize()) {
                    SongListActionBar(
                        title = "歌曲",
                        totalCount = artistSongs.size,
                        displayCount = displaySongs.size,
                        isSearching = selection.isSearching,
                        isSelectMode = selection.isSelectMode,
                        isAllSelected = isAllSelected,
                        selectedCount = selection.selectedIds.size,
                        canSelect = artistSongs.isNotEmpty(),
                        onToggleSearch = selection::toggleSearch,
                        onToggleSelectMode = selection::toggleSelectMode,
                        onSelectAll = { selection.setAllSelected(displaySongs) },
                    )

                    if (selection.isSearching) {
                        SongSearchField(
                            query = selection.searchQuery,
                            onQueryChange = selection::onSearchQueryChange,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = if (selection.isSelectMode && selection.selectedIds.isNotEmpty()) 88.dp else 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (artistSongs.isEmpty()) {
                            item(key = "artist_songs_empty") {
                                EmptySectionHint("该歌手暂无歌曲")
                            }
                        } else if (displaySongs.isEmpty()) {
                            item(key = "artist_songs_search_empty") {
                                SearchEmptyHint(searchQuery = selection.searchQuery)
                            }
                        } else {
                            items(displaySongs, key = { "artist_song_${it.id}" }) { song ->
                                SongItem(
                                    song = song,
                                    isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                                    showDuration = true,
                                    isSelectMode = selection.isSelectMode,
                                    isSelected = selection.isSelected(song.id),
                                    onSongClick = {
                                        if (selection.isSelectMode) {
                                            selection.toggleSelected(song.id)
                                        } else {
                                            onSongClick(song)
                                        }
                                    },
                                    menuActions = listOf(
                                        SongItemAction(
                                            label = "添加到歌单",
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(R.drawable.list_alt_add_24),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            onClick = { songForSinglePlaylist = song },
                                        ),
                                        SongItemAction(
                                            label = "查看歌曲详情",
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            onClick = { songForInfo = song },
                                        ),
                                    ),
                                )
                            }
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

        // ── 底部多选操作栏 ──
        AnimatedVisibility(
            visible = selection.isSelectMode && selection.selectedIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SurfaceBar(
                selectedCount = selection.selectedIds.size,
                onAddToPlaylist = { showBatchDialog = true },
                onClear = selection::exitSelectMode,
            )
        }
    }

    // ── 批量添加到歌单 Dialog ──
    if (showBatchDialog && selectedSongs.isNotEmpty()) {
        BatchAddToPlaylistDialog(
            songs = selectedSongs,
            playlists = playlists,
            onDismiss = { showBatchDialog = false },
            onSelectPlaylist = { playlist ->
                onAddSongsToPlaylist(selectedSongs, playlist)
                showBatchDialog = false
                selection.exitSelectMode()
            },
            onCreatePlaylist = onCreatePlaylistWithResult,
        )
    }

    // ── 单曲添加到歌单 Dialog ──
    val singleSong = songForSinglePlaylist
    if (singleSong != null) {
        SingleSongAddToPlaylistDialog(
            song = singleSong,
            playlists = playlists,
            onDismiss = { songForSinglePlaylist = null },
            onSelectPlaylist = { playlist ->
                onAddSongToPlaylist(singleSong, playlist)
                songForSinglePlaylist = null
            },
            onCreatePlaylist = onCreatePlaylistWithResult,
        )
    }

    // ── 歌曲信息 Dialog ──
    val infoSong = songForInfo
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = { songForInfo = null; songInfo = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌手详情 Route
// ─────────────────────────────────────────────────────────────────────────────

@Stable
data class ArtistDetailRouteState(
    val artistName: String,
    val songs: List<Song>,
    val playlists: List<Playlist> = emptyList(),
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class ArtistDetailRouteActions(
    val onBack: () -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onAlbumClick: (String) -> Unit = {},
    val onAddSongToPlaylist: (Song, Playlist) -> Unit = { _, _ -> },
    val onAddSongsToPlaylist: (List<Song>, Playlist) -> Int = { _, _ -> 0 },
    val onCreatePlaylistWithResult: (String) -> Playlist? = { null },
)

@Composable
fun ArtistDetailRoute(
    state: ArtistDetailRouteState,
    actions: ArtistDetailRouteActions,
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
    showToast: (String) -> Unit = {},
) {
    ArtistDetailScreen(
        artistName = state.artistName,
        songs = state.songs,
        playlists = state.playlists,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onBack = actions.onBack,
        onSongClick = { song ->
            val context = state.songs.filter { state.artistName in it.parsedArtists }
            actions.onSongClick(song, context)
        },
        onAlbumClick = actions.onAlbumClick,
        loadSongInfo = loadSongInfo,
        onAddSongToPlaylist = { song, playlist ->
            actions.onAddSongToPlaylist(song, playlist)
            showToast("已添加到「${playlist.name}」")
        },
        onAddSongsToPlaylist = { songsToAdd, playlist ->
            val added = actions.onAddSongsToPlaylist(songsToAdd, playlist)
            showToast(
                if (added > 0) "已将 $added 首歌曲添加到「${playlist.name}」"
                else "这些歌曲已在「${playlist.name}」中",
            )
        },
        onCreatePlaylistWithResult = actions.onCreatePlaylistWithResult,
    )
}
