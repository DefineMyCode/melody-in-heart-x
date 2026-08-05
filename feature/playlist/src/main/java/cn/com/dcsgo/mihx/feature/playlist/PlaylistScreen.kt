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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import cn.com.dcsgo.mihx.ui.components.locateHighlightFlash
import cn.com.dcsgo.mihx.ui.components.rememberLocateHighlightState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }

    // ── 歌曲信息 Dialog：选定歌曲后异步加载元数据 ──
    LaunchedEffect(songForInfo) {
        val uri = songForInfo?.uri
        if (uri != null) {
            songInfo = songForInfo?.let { loadSongInfo(it) }
        }
    }

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
        SingleSongAddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = null },
            onSelectPlaylist = { playlist ->
                onAddSongToPlaylist(song, playlist)
                showAddToPlaylistDialog = null
            },
            onCreatePlaylist = onCreatePlaylistWithResult,
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

    val infoSong = songForInfo
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = { songForInfo = null; songInfo = null },
        )
    }

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
                    playlists = playlists,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    onSongClick = onSongClick,
                    onShowAddToPlaylist = { showAddToPlaylistDialog = it },
                    onShowRemoveConfirm = { song -> showRemoveConfirm = song to selectedPlaylist },
                    onShowSongDetail = { song -> songForInfo = song },
                    onPlayAll = { onPlayAllInPlaylist(selectedPlaylist, songs) },
                    onPlayAllFromEnd = { onPlayAllFromEndInPlaylist(selectedPlaylist, songs) },
                    onAddAllToQueue = { onAddAllToQueueInPlaylist(selectedPlaylist, songs) },
                    onAddAllToNextPlay = { onAddAllToNextPlayInPlaylist(selectedPlaylist, songs) },
                    onAddSongToQueue = onAddSongToQueue,
                    onAddSongToNextPlay = onAddSongToNextPlay,
                    onReorderSongs = { orderedSongIds ->
                        selectedPlaylist?.let { onReorderPlaylist(it.id, orderedSongIds) }
                    },
                    onAddSongsToPlaylist = onAddSongsToPlaylist,
                    onCreatePlaylistWithResult = onCreatePlaylistWithResult,
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
    playlists: List<Playlist>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongClick: (Song) -> Unit,
    onShowAddToPlaylist: (Song) -> Unit,
    onShowRemoveConfirm: (Song) -> Unit,
    onShowSongDetail: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onPlayAllFromEnd: () -> Unit,
    onAddAllToQueue: () -> Unit,
    onAddAllToNextPlay: () -> Unit,
    onAddSongToQueue: (Song) -> Unit,
    onAddSongToNextPlay: (Song) -> Unit,
    onReorderSongs: (List<Int>) -> Unit,
    onAddSongsToPlaylist: (List<Song>, Playlist) -> Unit,
    onCreatePlaylistWithResult: (String) -> Playlist?,
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
    val orderedSongs = orderedSongIds.mapNotNull { songsById[it] }

    // 多选 / 搜索状态
    val selection = rememberSongSelectionController()
    val displaySongs = selection.filterSongs(orderedSongs)
    val selectedSongs = selection.selectedSongs(displaySongs)
    val isAllSelected = selection.isAllSelected(displaySongs)

    // 多选模式下系统返回键先退出多选
    BackHandler(enabled = selection.isSelectMode) {
        selection.exitSelectMode()
    }

    // ── 滚动 / 定位状态 ──
    val listState = rememberLazyListState()
    val locateHighlight = rememberLocateHighlightState()
    val coroutineScope = rememberCoroutineScope()

    // ── Dialog 状态 ──
    var showBatchDialog by remember { mutableStateOf(false) }

    // 定位 FAB 仅在未多选且当前播放歌曲在当前显示列表中时显示
    val canLocate = !selection.isSelectMode && currentSong?.let { cs ->
        displaySongs.any { it.id == cs.id }
    } == true

    // 拖拽排序（sh.calvin.reorderable 库处理：长按拖拽、边缘自动滚动、条目移动动画等）
    var reorderDirty by remember { mutableStateOf(false) }
    val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
        orderedSongIds = orderedSongIds.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        reorderDirty = true
    }
    // 拖拽结束（onMove 不再触发）时把最终排序持久化一次，避免每次 onMove 都写库
    LaunchedEffect(Unit) {
        snapshotFlow { reorderableState.isAnyItemDragging }
            .collect { dragging ->
                if (!dragging && reorderDirty) {
                    reorderDirty = false
                    onReorderSongs(orderedSongIds)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                SongListActionBar(
                    title = "歌曲列表",
                    totalCount = songs.size,
                    displayCount = displaySongs.size,
                    isSearching = selection.isSearching,
                    isSelectMode = selection.isSelectMode,
                    isAllSelected = isAllSelected,
                    selectedCount = selection.selectedIds.size,
                    canSelect = songs.isNotEmpty(),
                    onToggleSearch = selection::toggleSearch,
                    onToggleSelectMode = selection::toggleSelectMode,
                    onSelectAll = { selection.setAllSelected(displaySongs) },
                )

                // 搜索框固定在列表上方（不作为 LazyColumn item），保证列表项索引 == 显示列表索引
                if (selection.isSearching) {
                    SongSearchField(
                        query = selection.searchQuery,
                        onQueryChange = selection::onSearchQueryChange,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    )
                }

                val dragEnabled = !selection.isSelectMode && !selection.isSearching
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(
                            bottom = if (selection.isSelectMode && selection.selectedIds.isNotEmpty()) 88.dp else 0.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (displaySongs.isEmpty()) {
                            item(key = "playlist_search_empty") {
                                SearchEmptyHint(searchQuery = selection.searchQuery)
                            }
                        } else {
                            items(displaySongs, key = { it.id }) { song ->
                                ReorderableItem(
                                    reorderableState,
                                    key = song.id,
                                ) { isDragging ->
                                    SongItem(
                                        song = song,
                                        isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                                        showDuration = true,
                                        isSelectMode = selection.isSelectMode,
                                        isSelected = selection.isSelected(song.id),
                                        modifier = Modifier
                                            .then(
                                                if (dragEnabled) Modifier.longPressDraggableHandle() else Modifier
                                            )
                                            .shadow(if (isDragging) 8.dp else 0.dp, RoundedCornerShape(12.dp))
                                            .locateHighlightFlash(song.id, locateHighlight),
                                    onSongClick = {
                                        if (selection.isSelectMode) {
                                            selection.toggleSelected(song.id)
                                        } else {
                                            onSongClick(song)
                                        }
                                    },
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
                                            label = "查看歌曲详情",
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            },
                                            onClick = { onShowSongDetail(song) },
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
            }
        }

        // ── 定位当前播放 FAB ──
        AnimatedVisibility(
            visible = canLocate,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    currentSong?.let { cs ->
                        val index = displaySongs.indexOfFirst { it.id == cs.id }
                        locateHighlight.trigger(cs.id)
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
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

        // ── 批量添加到歌单 Dialog（排除当前歌单） ──
        if (showBatchDialog && selectedSongs.isNotEmpty()) {
            BatchAddToPlaylistDialog(
                songs = selectedSongs,
                playlists = playlists.filterNot { it.id == selectedPlaylist.id },
                onDismiss = { showBatchDialog = false },
                onSelectPlaylist = { playlist ->
                    onAddSongsToPlaylist(selectedSongs, playlist)
                    showBatchDialog = false
                    selection.exitSelectMode()
                },
                onCreatePlaylist = onCreatePlaylistWithResult,
            )
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
        // 歌曲 ID → 歌曲 映射只建一次，供所有歌单项 O(1) 取封面（须在 LazyColumn 组合作用域之外）
        val songById = remember(songs) { songs.associateBy { it.id } }
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
                        songById = songById,
                        onPlaylistClick = onPlaylistClick,
                        onDelete = { onDelete(playlist) },
                        onRename = { onRename(playlist) }
                    )
                }
            }
        }
    }
}
