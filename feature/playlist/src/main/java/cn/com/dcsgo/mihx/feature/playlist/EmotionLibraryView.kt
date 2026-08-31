package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.EmotionSongUiRow
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.BatchAddToPlaylistDialog
import cn.com.dcsgo.mihx.ui.components.SingleSongAddToPlaylistDialog
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import cn.com.dcsgo.mihx.ui.components.SongItemAction

/**
 * 曲库「情绪」Tab: 已分析歌曲列表.
 * 搜索走曲库全局搜索框; 本页保留 词条过滤 / 已分析·手动标记分段 /
 * 多选批量加歌单 / 点行播放 / ⋮菜单(信息+加歌单), 组件与本地音乐一致.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionLibraryView(
    rows: List<EmotionSongUiRow>,
    playlists: List<Playlist>,
    currentSong: Song?,
    isPlaying: Boolean,
    /** 曲库全局搜索词(歌名/歌手) */
    searchQuery: String,
    onSongClick: (Song) -> Unit,
    /** Route 层已包装: 弹 toast(与本地音乐/歌手/专辑页一致) */
    onAddSongsToPlaylist: (List<Song>, Playlist) -> Unit,
    onCreatePlaylist: (String) -> Playlist?,
    loadSongInfo: suspend (Song) -> SongInfo?,
) {
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showCorrectedOnly by remember { mutableStateOf(false) }
    var songForAdd by remember { mutableStateOf<Song?>(null) }
    var songForInfo by remember { mutableStateOf<Song?>(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }
    val selection = rememberSongSelectionController()

    val correctedCount = rows.count { it.corrected }
    val filtered = remember(rows, searchQuery, selectedTags, showCorrectedOnly) {
        rows.filter { row ->
            (!showCorrectedOnly || row.corrected) &&
                (searchQuery.isBlank() ||
                    row.song.title.contains(searchQuery, ignoreCase = true) ||
                    row.song.artist.contains(searchQuery, ignoreCase = true)) &&
                (selectedTags.isEmpty() || row.tags.any { it in selectedTags })
        }.sortedWith(
            compareByDescending<EmotionSongUiRow> { it.corrected }
                .thenByDescending { it.tags.isNotEmpty() }
                .thenBy { it.song.title }
        )
    }
    val tagOptions = remember(rows) { rows.flatMap { it.tags }.distinct().sorted() }
    val filteredSongs = filtered.map { it.song }
    val selectedSongs = selection.selectedSongs(filteredSongs)

    Column(modifier = Modifier.fillMaxSize()) {
        // 已选词条: 置顶固定行(仿校准弹窗"已选"), 点按移除, 尾部可整体清空
        if (selectedTags.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(selectedTags.toList(), key = { "esel_$it" }, contentType = { "tag" }) { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { selectedTags = selectedTags - tag },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                    )
                }
                item(key = "esel_clear", contentType = "tag") {
                    TextButton(onClick = { selectedTags = emptySet() }) {
                        Text("清空", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        // 词条过滤(横向 chips)
        if (tagOptions.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tagOptions, key = { "etab_$it" }, contentType = { "tag" }) { tag ->
                    FilterChip(
                        selected = tag in selectedTags,
                        onClick = {
                            selectedTags = if (tag in selectedTags) {
                                selectedTags - tag
                            } else {
                                selectedTags + tag
                            }
                        },
                        label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
        // 已分析 / 手动标记 分段 + 多选开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                SegmentedButton(
                    selected = !showCorrectedOnly,
                    onClick = { showCorrectedOnly = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("已分析 ${rows.size}") }
                SegmentedButton(
                    selected = showCorrectedOnly,
                    onClick = { showCorrectedOnly = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("手动标记 $correctedCount") }
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { selection.toggleSelectMode() }) {
                Text(if (selection.isSelectMode) "退出多选" else "多选")
            }
        }
        if (filtered.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (rows.isEmpty()) "还没有分析过的歌曲" else "没有符合条件的歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (rows.isEmpty()) {
                    Text(
                        text = "去「我的 → 歌曲情绪分析」点立即扫描",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(filtered, key = { it.song.id }, contentType = { "emotion_song" }) { row ->
                    EmotionLibrarySongItem(
                        row = row,
                        isCurrentPlaying = isPlaying && currentSong?.id == row.song.id,
                        isSelectMode = selection.isSelectMode,
                        isSelected = selection.isSelected(row.song.id),
                        onSongClick = { song ->
                            if (selection.isSelectMode) {
                                selection.toggleSelected(song.id)
                            } else {
                                onSongClick(song)
                            }
                        },
                        menuActions = listOf(
                            SongItemAction(
                                label = "歌曲信息",
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = { songForInfo = row.song },
                            ),
                            SongItemAction(
                                label = "添加到歌单",
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.list_alt_add_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = { songForAdd = row.song },
                            ),
                        ),
                    )
                }
            }
        }

        // 多选底部栏(照本地音乐交互): Column 内末子, 贴在内容区底部
        if (selection.isSelectMode) {
            Card(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已选 ${selection.selectedIds.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        selection.setAllSelected(
                            if (selection.isAllSelected(filteredSongs)) emptyList()
                            else filteredSongs
                        )
                    }
                ) {
                    Text(if (selection.isAllSelected(filteredSongs)) "取消全选" else "全选")
                }
                TextButton(
                    enabled = selection.selectedIds.isNotEmpty(),
                    onClick = { showBatchDialog = true },
                ) {
                    Text("添加到歌单")
                }
                TextButton(onClick = { selection.exitSelectMode() }) {
                    Text("退出")
                }
                }
            }
        }
    }

    // ── 对话框 ──
    val addSong = songForAdd
    if (addSong != null) {
        SingleSongAddToPlaylistDialog(
            song = addSong,
            playlists = playlists,
            onDismiss = { songForAdd = null },
            onSelectPlaylist = { playlist ->
                songForAdd = null
                onAddSongsToPlaylist(listOf(addSong), playlist)
            },
            onCreatePlaylist = onCreatePlaylist,
        )
    }

    if (showBatchDialog && selectedSongs.isNotEmpty()) {
        BatchAddToPlaylistDialog(
            songs = selectedSongs,
            playlists = playlists,
            onDismiss = { showBatchDialog = false },
            onSelectPlaylist = { playlist ->
                showBatchDialog = false
                onAddSongsToPlaylist(selectedSongs, playlist)
                selection.exitSelectMode()
            },
            onCreatePlaylist = onCreatePlaylist,
        )
    }

    val infoSong = songForInfo
    LaunchedEffect(infoSong) {
        if (infoSong != null) songInfo = loadSongInfo(infoSong)
    }
    val currentSongInfo = songInfo
    if (infoSong != null && currentSongInfo != null) {
        SongInfoDialog(
            song = infoSong,
            songInfo = currentSongInfo,
            onDismiss = {
                songForInfo = null
                songInfo = null
            },
        )
    }
}

/** 情绪 Tab 行: 本地音乐同款 SongItem, 词条挂在歌手下一行. */
@Composable
private fun EmotionLibrarySongItem(
    row: EmotionSongUiRow,
    isCurrentPlaying: Boolean,
    isSelectMode: Boolean,
    isSelected: Boolean,
    onSongClick: (Song) -> Unit,
    menuActions: List<SongItemAction>,
) {
    SongItem(
        song = row.song,
        isCurrentPlaying = isCurrentPlaying,
        isSelectMode = isSelectMode,
        isSelected = isSelected,
        onSongClick = onSongClick,
        menuActions = menuActions,
        subline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (row.tags.isEmpty()) "未定"
                    else row.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (row.tags.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row.corrected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "已校准",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
    )
}
