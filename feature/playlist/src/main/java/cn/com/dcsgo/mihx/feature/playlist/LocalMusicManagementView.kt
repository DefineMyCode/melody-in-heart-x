package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.locateHighlightFlash
import cn.com.dcsgo.mihx.ui.components.rememberLocateHighlightState
import kotlinx.coroutines.launch

/**
 * 本地音乐管理视图
 *
 * 提供本地歌曲的搜索、多选、添加歌单、删除、信息查看和文件管理入口。
 */
@Composable
fun LocalMusicManagementView(
    songs: List<Song>,
    playlists: List<Playlist>,
    currentSong: Song? = null,
    isPlaying: Boolean = false,
    isImporting: Boolean = false,
    importProgress: Int = 0,
    importTotal: Int = 0,
    onAddFolderClick: () -> Unit,
    onSongClick: (Song) -> Unit = {},
    onAddSongsToPlaylist: (List<Song>, Playlist) -> Unit = { _, _ -> },
    onDeleteSong: (Song) -> Unit = {},
    onCreatePlaylist: (String) -> Playlist? = { _ -> null },
    onShowVersionManagement: () -> Unit = {},
    onShowQuickSkipSongs: () -> Unit = {},
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
) {
    // 只显示有本地 URI 的歌曲（用户真正导入的文件）
    val localSongs = remember(songs) { songs.filter { it.uri != null } }

    // ── 搜索状态 ──
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // ── 多选状态 ──
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateOf(setOf<Int>()) }

    // ── Dialog 状态 ──
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songForSinglePlaylist: Song? by remember { mutableStateOf(null) }
    var songForInfo: Song? by remember { mutableStateOf(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    var songForDelete: Song? by remember { mutableStateOf(null) }

    // 搜索过滤
    val displaySongs by remember(searchQuery, localSongs) {
        derivedStateOf {
            if (searchQuery.isBlank()) localSongs
            else localSongs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedSongs by remember(displaySongs, selectedIds.value) {
        derivedStateOf {
            displaySongs.filter { it.id in selectedIds.value }
        }
    }

    val isAllSelected by remember(displaySongs, selectedIds.value) {
        derivedStateOf {
            displaySongs.isNotEmpty() && displaySongs.all { it.id in selectedIds.value }
        }
    }

    // ── Dialog 渲染 ──
    if (showAddToPlaylistDialog && selectedSongs.isNotEmpty()) {
        BatchAddToPlaylistDialog(
            songs = selectedSongs,
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onSelectPlaylist = { playlist ->
                onAddSongsToPlaylist(selectedSongs, playlist)
                showAddToPlaylistDialog = false
                isSelectMode = false
                selectedIds.value = emptySet()
            },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    val singleSong = songForSinglePlaylist
    if (singleSong != null) {
        SingleSongAddToPlaylistDialog(
            song = singleSong,
            playlists = playlists,
            onDismiss = { songForSinglePlaylist = null },
            onSelectPlaylist = { playlist ->
                onAddSongsToPlaylist(listOf(singleSong), playlist)
                songForSinglePlaylist = null
            },
            onCreatePlaylist = onCreatePlaylist
        )
    }

    val infoSong = songForInfo
    LaunchedEffect(infoSong) {
        val uri = infoSong?.uri
        if (uri != null) {
            songInfo = infoSong?.let { loadSongInfo(it) }
        }
    }
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = { songForInfo = null; songInfo = null }
        )
    }

    val deleteSong = songForDelete
    if (deleteSong != null) {
        DeleteSongConfirmDialog(
            song = deleteSong,
            onDismiss = { songForDelete = null },
            onConfirm = {
                onDeleteSong(deleteSong)
                songForDelete = null
            }
        )
    }

    // ── 滚动状态 ──
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val locateHighlight = rememberLocateHighlightState()

    // 计算当前播放歌曲在列表中的索引
    // key 的顺序：file_mgmt(0), local_header(1), [search_box(2)], song_0, song_1, ...
    val currentSongIndexInList by remember(currentSong, displaySongs, isSearching) {
        derivedStateOf {
            if (currentSong == null) -1
            else {
                val posInDisplay = displaySongs.indexOfFirst { it.id == currentSong.id }
                if (posInDisplay < 0) -1
                else {
                    val headerCount = if (isSearching) 3 else 2
                    headerCount + posInDisplay
                }
            }
        }
    }

    val canLocate by remember(currentSongIndexInList) {
        derivedStateOf { currentSongIndexInList >= 0 }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = if (isSelectMode) 80.dp else 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 文件管理区域
            item(key = "file_mgmt", contentType = "header") {
                FileManagementSection(
                    isImporting = isImporting,
                    importProgress = importProgress,
                    importTotal = importTotal,
                    onAddFolderClick = onAddFolderClick,
                    onVersionManagementClick = onShowVersionManagement,
                    onQuickSkipSongsClick = onShowQuickSkipSongs
                )
            }

            // 本地音乐标题 + 操作按钮
            item(key = "local_header", contentType = "header") {
                LocalMusicHeader(
                    localSongs = localSongs,
                    displaySongs = displaySongs,
                    isSearching = isSearching,
                    isSelectMode = isSelectMode,
                    isAllSelected = isAllSelected,
                    selectedCount = selectedIds.value.size,
                    onToggleSearch = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            isSearching = true
                        }
                    },
                    onToggleSelectMode = {
                        if (isSelectMode) {
                            isSelectMode = false
                            selectedIds.value = emptySet()
                        } else {
                            isSelectMode = true
                        }
                    },
                    onSelectAll = {
                        if (isAllSelected) {
                            selectedIds.value = emptySet()
                        } else {
                            selectedIds.value = displaySongs.map { it.id }.toSet()
                        }
                    }
                )
            }

            // 搜索框
            if (isSearching) {
                item(key = "search_box", contentType = "header") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "搜索歌曲名或艺术家",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { /* 已实时过滤 */ }),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        )
                    )
                }
            }

            if (localSongs.isEmpty()) {
                item(key = "empty_hint", contentType = "footer") {
                    EmptyMusicHint()
                }
            } else if (displaySongs.isEmpty()) {
                item(key = "search_empty", contentType = "footer") {
                    SearchEmptyHint(searchQuery = searchQuery)
                }
            } else {
                items(
                    items = displaySongs,
                    key = { "song_${it.id}" },
                    contentType = { "song_item" }
                ) { song ->
                    val isSelected = song.id in selectedIds.value
                    LocalSongItem(
                        song = song,
                        isCurrentPlaying = isPlaying && currentSong?.id == song.id,
                        isSelectMode = isSelectMode,
                        isSelected = isSelected,
                        modifier = Modifier.locateHighlightFlash(song.id, locateHighlight),
                        onClick = {
                            if (isSelectMode) {
                                val newSet = if (isSelected) {
                                    selectedIds.value - song.id
                                } else {
                                    selectedIds.value + song.id
                                }
                                selectedIds.value = newSet
                            } else {
                                onSongClick(song)
                            }
                        },
                        onShowInfo = { songForInfo = it },
                        onAddToPlaylist = { songForSinglePlaylist = it },
                        onDelete = { songForDelete = it }
                    )
                }
            }
        }

        // ── 底部悬浮按钮（回到顶部 + 快速定位） ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = 16.dp,
                    bottom = if (isSelectMode && selectedIds.value.isNotEmpty()) 88.dp else 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 回到顶部 FAB
            AnimatedVisibility(
                visible = localSongs.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "回到顶部"
                    )
                }
            }

            // 快速定位 FAB
            AnimatedVisibility(
                visible = canLocate,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                FloatingActionButton(
                    onClick = {
                        locateHighlight.trigger(currentSong?.id)
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentSongIndexInList)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "定位到当前播放"
                    )
                }
            }
        }

        // ── 底部多选操作栏 ──
        AnimatedVisibility(
            visible = isSelectMode && selectedIds.value.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SurfaceBar(
                selectedCount = selectedIds.value.size,
                onAddToPlaylist = { showAddToPlaylistDialog = true },
                onClear = {
                    selectedIds.value = emptySet()
                    isSelectMode = false
                }
            )
        }
    }
}
