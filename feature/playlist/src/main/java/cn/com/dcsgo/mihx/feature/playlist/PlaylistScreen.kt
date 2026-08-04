package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.LocateHighlightState
import cn.com.dcsgo.mihx.ui.components.locateHighlightFlash
import cn.com.dcsgo.mihx.ui.components.rememberLocateHighlightState
import kotlin.math.roundToInt

@Composable
fun PlaylistScreen(
    playlists: List<Playlist>,
    songs: List<Song>,
    libraryArtists: List<ArtistEntry> = emptyList(),
    libraryAlbums: List<AlbumEntry> = emptyList(),
    selectedPlaylist: Playlist?,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Song) -> Unit,
    onLocalSongClick: (Song) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    onBackClick: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onRenamePlaylist: (Playlist, String) -> Unit,
    onAddSongToPlaylist: (Song, Playlist) -> Unit,
    onRemoveSongFromPlaylist: (Song, Playlist) -> Unit,
    onReorderPlaylist: (Int, List<Int>) -> Unit = { _, _ -> },
    // 本地音乐管理相关
    isImporting: Boolean = false,
    importProgress: Int = 0,
    importTotal: Int = 0,
    onAddFolderClick: () -> Unit = {},
    onAddSongsToPlaylist: (List<Song>, Playlist) -> Unit = { _, _ -> },
    onDeleteSong: (Song) -> Unit = {},
    onCreatePlaylistWithResult: (String) -> Playlist? = { null },
    onShowVersionManagement: () -> Unit = {},
    onShowQuickSkipSongs: () -> Unit = {},
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
    // 播放队列相关回调
    onPlayAllInPlaylist: (Playlist, List<Song>) -> Unit = { _, _ -> },
    onPlayAllFromEndInPlaylist: (Playlist, List<Song>) -> Unit = { _, _ -> },
    onAddAllToQueueInPlaylist: (Playlist, List<Song>) -> Unit = { _, _ -> },
    onAddAllToNextPlayInPlaylist: (Playlist, List<Song>) -> Unit = { _, _ -> },
    onAddSongToQueue: (Song) -> Unit = {},
    onAddSongToNextPlay: (Song) -> Unit = {},
) {
    // ── 本地音乐视图切换 ──
    // 用 rememberSaveable 保存，进入多版本管理/秒切歌单等子页面返回后仍停留在本地音乐视图
    var showLocalMusic by rememberSaveable { mutableStateOf(false) }

    // ── 曲库类目切换 ──
    var selectedLibraryTab by rememberSaveable {
        mutableStateOf(LibraryTab.PLAYLISTS)
    }

    // ── 曲库搜索 ──
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }

    // ── 专辑：隐藏仅有一首歌曲的专辑（默认开启） ──
    var hideSingleSongAlbums by rememberSaveable { mutableStateOf(true) }

    // ── 歌手：隐藏仅有一首歌曲的歌手（默认开启） ──
    var hideSingleSongArtists by rememberSaveable { mutableStateOf(true) }

    // 本地音乐视图内按系统返回键时，先退出本地音乐回到歌单管理页面
    BackHandler(enabled = showLocalMusic) {
        showLocalMusic = false
    }

    // ── Dialog 状态 ──
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Playlist?>(null) }
    var showRenameDialog by remember { mutableStateOf<Playlist?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf<Song?>(null) }
    var showRemoveConfirm by remember { mutableStateOf<Pair<Song, Playlist>?>(null) }

    // ── Dialog 渲染 ──
    if (showCreateDialog) {
        CreatePlaylistDialog(
            existingNames = playlists.map { it.name },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    showDeleteConfirm?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { showDeleteConfirm = null },
            onConfirm = {
                onDeletePlaylist(playlist)
                showDeleteConfirm = null
                onBackClick()
            }
        )
    }

    showRenameDialog?.let { playlist ->
        RenamePlaylistDialog(
            currentName = playlist.name,
            existingNames = playlists.map { it.name },
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                onRenamePlaylist(playlist, newName)
                showRenameDialog = null
            }
        )
    }

    showAddToPlaylistDialog?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = null },
            onSelectPlaylist = { playlist ->
                onAddSongToPlaylist(song, playlist)
                showAddToPlaylistDialog = null
            }
        )
    }

    showRemoveConfirm?.let { (song, playlist) ->
        RemoveSongConfirmDialog(
            songTitle = song.title,
            playlistName = playlist.name,
            onDismiss = { showRemoveConfirm = null },
            onConfirm = {
                onRemoveSongFromPlaylist(song, playlist)
                showRemoveConfirm = null
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // ── 歌单详情页的滚动状态（必须在 Box 层级声明，FAB 也要用） ──
        val detailListState = rememberLazyListState()
        val detailScope = rememberCoroutineScope()
        val detailLocateHighlight = rememberLocateHighlightState()
        val currentSongInDetail by remember(currentSong, selectedPlaylist) {
            derivedStateOf {
                currentSong?.let { cs ->
                    songs.indexOfFirst { it.id == cs.id }.takeIf { it >= 0 }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPlaylist != null) {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = selectedPlaylist.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = if (showLocalMusic) "本地音乐" else "曲库",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    // 本地音乐管理入口（文字按钮）
                    TextButton(onClick = { showLocalMusic = !showLocalMusic }) {
                        Text(
                            text = if (showLocalMusic) "曲库" else "本地音乐",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (selectedPlaylist != null) {
                // ── 歌单详情页 ──
                PlaylistDetailView(
                    selectedPlaylist = selectedPlaylist,
                    songs = songs,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = onSongClick,
                    onShowAddToPlaylist = { showAddToPlaylistDialog = it },
                    onShowRemoveConfirm = { song -> showRemoveConfirm = song to selectedPlaylist },
                    onPlayAll = { onPlayAllInPlaylist(selectedPlaylist, songs) },
                    onPlayAllFromEnd = { onPlayAllFromEndInPlaylist(selectedPlaylist, songs) },
                    onAddAllToQueue = { onAddAllToQueueInPlaylist(selectedPlaylist, songs) },
                    onAddAllToNextPlay = { onAddAllToNextPlayInPlaylist(selectedPlaylist, songs) },
                    onAddSongToQueue = onAddSongToQueue,
                    onAddSongToNextPlay = onAddSongToNextPlay,
                    onReorderSongs = { orderedSongIds ->
                        selectedPlaylist?.let { onReorderPlaylist(it.id, orderedSongIds) }
                    },
                    listState = detailListState,
                    locateHighlight = detailLocateHighlight,
                )
            } else if (showLocalMusic) {
                // ── 本地音乐管理页 ──
                LocalMusicManagementView(
                    songs = songs,
                    playlists = playlists,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    isImporting = isImporting,
                    importProgress = importProgress,
                    importTotal = importTotal,
                    onAddFolderClick = onAddFolderClick,
                    onSongClick = onLocalSongClick,
                    onAddSongsToPlaylist = onAddSongsToPlaylist,
                    onDeleteSong = onDeleteSong,
                    onCreatePlaylist = onCreatePlaylistWithResult,
                    onShowVersionManagement = onShowVersionManagement,
                    onShowQuickSkipSongs = onShowQuickSkipSongs,
                    loadSongInfo = loadSongInfo,
                )
            } else {
                // ── 曲库页（歌单 / 歌手 / 专辑） ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // 搜索框
                    LibrarySearchField(
                        query = librarySearchQuery,
                        onQueryChange = { librarySearchQuery = it },
                    )
                    LibraryTabRow(
                        selectedTab = selectedLibraryTab,
                        onTabSelected = { selectedLibraryTab = it },
                    )
                    when (selectedLibraryTab) {
                        LibraryTab.PLAYLISTS -> PlaylistListView(
                            playlists = playlists.filter { playlist ->
                                librarySearchQuery.isBlank() ||
                                    playlist.name.contains(librarySearchQuery, ignoreCase = true)
                            },
                            songs = songs,
                            onPlaylistClick = onPlaylistClick,
                            onDelete = { showDeleteConfirm = it },
                            onRename = { showRenameDialog = it }
                        )
                        LibraryTab.ARTISTS -> ArtistListView(
                            artists = libraryArtists.filter { artist ->
                                librarySearchQuery.isBlank() ||
                                    artist.name.contains(librarySearchQuery, ignoreCase = true)
                            },
                            // 有搜索内容时隐藏开关
                            hideSingleSongArtists = librarySearchQuery.isBlank() && hideSingleSongArtists,
                            onHideSingleSongArtistsChange = { hideSingleSongArtists = it },
                            onArtistClick = onArtistClick,
                        )
                        LibraryTab.ALBUMS -> AlbumListView(
                            albums = libraryAlbums.filter { album ->
                                librarySearchQuery.isBlank() ||
                                    album.name.contains(librarySearchQuery, ignoreCase = true) ||
                                    album.artistNames.any { it.contains(librarySearchQuery, ignoreCase = true) }
                            },
                            // 有搜索内容时隐藏开关
                            hideSingleSongAlbums = librarySearchQuery.isBlank() && hideSingleSongAlbums,
                            onHideSingleSongAlbumsChange = { hideSingleSongAlbums = it },
                            onAlbumClick = onAlbumClick,
                        )
                    }
                }
            }
        }

        // ── FAB ──
        if (selectedPlaylist == null && !showLocalMusic && selectedLibraryTab == LibraryTab.PLAYLISTS) {
            // 歌单列表页：创建歌单
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "创建歌单")
            }
        } else if (selectedPlaylist != null) {
            // 歌单详情页：快速定位当前播放
            AnimatedVisibility(
                visible = currentSongInDetail != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        detailLocateHighlight.trigger(currentSong?.id)
                        currentSongInDetail?.let { index ->
                            detailScope.launch {
                                detailListState.animateScrollToItem(index)
                            }
                        }
                    },
                    // 对齐设计系统 §5.11：FAB 用 accent 底
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "定位到当前播放"
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌单详情页
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistDetailView(
    selectedPlaylist: Playlist,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShowAddToPlaylist: (Song) -> Unit,
    onShowRemoveConfirm: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onPlayAllFromEnd: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onAddAllToNextPlay: () -> Unit,
    onAddSongToQueue: (Song) -> Unit,
    onAddSongToNextPlay: (Song) -> Unit,
    onReorderSongs: (List<Int>) -> Unit,
    listState: LazyListState,
    locateHighlight: LocateHighlightState,
) {
    // 歌曲条目按歌单顺序展示，长按拖拽排序，外部变化（移除等）时同步
    val songsById = remember(songs) { songs.associateBy { it.id } }
    val songIdSet = remember(songs) { songs.map { it.id }.toSet() }
    var orderedSongIds by remember(selectedPlaylist.id) { mutableStateOf(songs.map { it.id }) }
    LaunchedEffect(songIdSet) {
        if (orderedSongIds.toSet() != songIdSet) {
            orderedSongIds = songs.map { it.id }
        }
    }
    val displayedSongs = orderedSongIds.mapNotNull { songsById[it] }
    val lastIndex = displayedSongs.lastIndex

    // 拖拽排序状态
    val itemHeightPx = with(LocalDensity.current) { 68.dp.toPx() }
    var dragIndex by remember(selectedPlaylist.id) { mutableStateOf(-1) }
    var targetIndex by remember(selectedPlaylist.id) { mutableStateOf(-1) }
    var dragOffsetY by remember(selectedPlaylist.id) { mutableStateOf(0f) }
    // 松手后需要滚动到的索引：重排后按数字索引锚定会导致拖到顶部时被顶出视口，需校正
    var pendingScrollIndex by remember(selectedPlaylist.id) { mutableStateOf(-1) }
    LaunchedEffect(pendingScrollIndex) {
        if (pendingScrollIndex >= 0) {
            if (pendingScrollIndex < listState.firstVisibleItemIndex) {
                listState.animateScrollToItem(pendingScrollIndex)
            }
            pendingScrollIndex = -1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))
        // 歌单信息
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (songs.isEmpty()) "暂无歌曲" else "${songs.size} 首歌曲 · 共 ${
                        formatHoursMinutes(
                            songs.sumOf { it.durationMs })}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── 播放按钮区 ──
        if (songs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onPlayAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.vertical_align_bottom_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "从头播放 (${songs.size})")
                    }
                    FilledTonalButton(
                        onClick = onPlayAllFromEnd,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.vertical_align_top_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "从尾播放 (${songs.size})")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAddAllToQueue,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.music_note_add_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "加入队尾 (${songs.size})")
                    }
                    FilledTonalButton(
                        onClick = onAddAllToNextPlay,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.skip_next_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "下一首播放 (${songs.size})")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (songs.isEmpty()) {
            EmptyPlaylistDetailHint()
        } else {
            Text(
                text = "歌曲列表",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayedSongs, key = { it.id }) { song ->
                    val index = displayedSongs.indexOfFirst { it.id == song.id }
                    SongItem(
                        song = song,
                        isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                        showDuration = true,
                        modifier = Modifier
                            .zIndex(if (index == dragIndex) 1f else 0f)
                            .graphicsLayer {
                                translationY = when {
                                    index == dragIndex -> dragOffsetY
                                    targetIndex > dragIndex && index in (dragIndex + 1)..targetIndex -> -itemHeightPx
                                    targetIndex < dragIndex && index in targetIndex until dragIndex -> itemHeightPx
                                    else -> 0f
                                }
                            }
                            .pointerInput(song.id, index) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragIndex = index
                                        targetIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        if (dragIndex >= 0 && targetIndex >= 0 && dragIndex != targetIndex) {
                                            orderedSongIds = orderedSongIds.toMutableList().apply {
                                                add(targetIndex, removeAt(dragIndex))
                                            }
                                            onReorderSongs(orderedSongIds)
                                            pendingScrollIndex = targetIndex
                                        }
                                        dragIndex = -1
                                        targetIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragIndex = -1
                                        targetIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val deltaSlots = (dragOffsetY / itemHeightPx).roundToInt()
                                        targetIndex = (index + deltaSlots).coerceIn(0, lastIndex)
                                    },
                                )
                            }
                            .locateHighlightFlash(song.id, locateHighlight),
                        onSongClick = onSongClick,
                        menuActions = listOf(
                            SongItemAction(
                                label = "加入播放队列",
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.music_note_add_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = { onAddSongToQueue(song) },
                            ),
                            SongItemAction(
                                label = "下一首播放",
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_next),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = { onAddSongToNextPlay(song) },
                            ),
                            SongItemAction(
                                label = "添加到歌单",
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(id = R.drawable.list_alt_add_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = { onShowAddToPlaylist(song) },
                            ),
                            SongItemAction(
                                label = "移除",
                                destructive = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = { onShowRemoveConfirm(song) },
                            ),
                        ),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 歌单列表页
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistListView(
    playlists: List<Playlist>,
    songs: List<Song>,
    onPlaylistClick: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
) {
    if (songs.isEmpty() && playlists.isEmpty()) {
        EmptyLibraryHint()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, bottom = 80.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (playlists.isEmpty()) {
                item(key = "playlist_empty") {
                    Spacer(modifier = Modifier.height(20.dp))
                    EmptySectionHint("您还没有歌单，点击右下角 + 创建~")
                }
            } else {
                items(playlists, key = { "playlist_${it.id}" }) { playlist ->
                    PlaylistItem(
                        playlist = playlist,
                        songs = songs,
                        onPlaylistClick = onPlaylistClick,
                        onDelete = { onDelete(playlist) },
                        onRename = { onRename(playlist) }
                    )
                }
            }
        }
    }
}
