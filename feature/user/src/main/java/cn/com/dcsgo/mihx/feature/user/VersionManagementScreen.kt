package cn.com.dcsgo.mihx.feature.user

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.ui.components.SongInfoDialog
import cn.com.dcsgo.mihx.ui.components.locateHighlightFlash
import cn.com.dcsgo.mihx.ui.components.rememberLocateHighlightState
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
// 多版本管理主页面（独立全屏页面，替代之前的弹窗）
// ─────────────────────────────────────────────────────────────────

/**
 * 多版本歌曲管理页面。
 *
 * 以独立全屏页面形式展示所有有多版本的歌曲分组，支持：
 * - 点击分组标题行展开/折叠查看版本列表
 * - 点击版本行直接播放该版本（整行可点击）
 * - 快速定位正在播放的歌曲（FAB 按钮）
 * - 版本关联/移出分组（"更多"菜单）
 * - 搜索过滤分组（按歌曲名）
 * - 删除某版本（需二次确认）
 *
 * 性能优化策略：
 * - 使用 `remember(songs)` 在数据变化时才重新计算分组，避免每次重组都做 O(n) 排序
 * - LazyColumn + `itemsIndexed` 只渲染可见 item，千级以上分组也流畅
 * - 展开状态由各 VersionGroupRow 内部管理，外部只传 forceExpanded 用于快速定位
 * - 搜索使用 `derivedStateOf` 延迟计算，防止输入时频繁重建列表
 *
 * @param songs             当前所有本地歌曲列表（来自 ViewModel uiState）
 * @param currentSong       当前正在播放的歌曲
 * @param isPlaying         播放器是否在播放
 * @param onBack            返回上一页（关闭本页面）
 * @param onPlayVersion     播放某个版本（歌曲 + 所有同分组歌曲作为上下文队列）
 * @param onAddToQueue      将某版本添加到播放队列
 * @param onDeleteSong      删除某首歌曲（从本地文件和所有歌单中移除）
 * @param onDetachVersion   将某版本移出当前分组（独立成单曲）
 * @param onReassignVersion 将某版本关联到其他歌曲分组的回调（传出 song 和选定的 targetSong）
 * @param onCopied          复制歌曲名后的回调（用于显示 Toast）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionManagementScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayVersion: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onDeleteSong: (Song) -> Unit,
    onDetachVersion: (Song) -> Unit,
    onReassignVersion: (song: Song, targetSong: Song) -> Unit,
    onCopied: (text: String) -> Unit = {},
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
) {
    // ── 分组计算（remember key = songs，仅在数据变化时重算）
    val versionGroups = remember(songs) {
        songs
            .filter { it.uri != null }
            .groupBy { it.groupKey }
            .filter { (_, list) -> list.size >= 2 }
            .values
            .map { songList ->
                val sorted = songList.sortedByDescending { it.sampleRate }
                SongVersionGroup(
                    groupKey = songList.first().groupKey,
                    displayTitle = songList.first().title,
                    albumArtUri = sorted.firstOrNull { it.albumArtUri != null }?.albumArtUri,
                    versions = sorted
                )
            }
            .sortedBy { it.displayTitle }
    }

    // ── 搜索状态
    var searchQuery by remember { mutableStateOf("") }

    val displayGroups by remember(versionGroups, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) versionGroups
            else versionGroups.filter {
                it.displayTitle.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // ── 快速定位：找到当前播放歌曲所在的分组索引
    val currentGroupIndex by remember(currentSong, displayGroups) {
        derivedStateOf {
            if (currentSong == null) -1
            else displayGroups.indexOfFirst { group ->
                group.versions.any { it.id == currentSong.id }
            }
        }
    }

    // ── 强制展开的分组 key（用于快速定位后展开对应分组）
    var locateGroupKey by remember { mutableStateOf<String?>(null) }
    val locateHighlight = rememberLocateHighlightState()

    // ── LazyColumn 滚动状态
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // ── Dialog 状态
    var songToDelete: Song? by remember { mutableStateOf(null) }
    var songToReassign: Song? by remember { mutableStateOf(null) }

    // ── 歌曲信息 Dialog 状态（选定歌曲后异步加载元数据）
    var songForInfo: Song? by remember { mutableStateOf(null) }
    var songInfo by remember { mutableStateOf<SongInfo?>(null) }
    LaunchedEffect(songForInfo) {
        val uri = songForInfo?.uri
        if (uri != null) {
            songInfo = songForInfo?.let { loadSongInfo(it) }
        }
    }

    // ── 删除确认 Dialog ──
    songToDelete?.let { song ->
        DeleteVersionConfirmDialog(
            song = song,
            onDismiss = { songToDelete = null },
            onConfirm = {
                onDeleteSong(song)
                songToDelete = null
            }
        )
    }

    // ── 关联选择 Dialog ──
    songToReassign?.let { song ->
        ReassignVersionDialog(
            song = song,
            allSongs = songs,
            onDismiss = { songToReassign = null },
            onConfirm = { targetSong ->
                onReassignVersion(song, targetSong)
                songToReassign = null
            },
            onCopied = onCopied
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "多版本管理",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (versionGroups.isNotEmpty()) {
                            Text(
                                text = "${versionGroups.size} 组 · ${versionGroups.sumOf { it.versions.size }} 首",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButtonPosition = FabPosition.Start,
        // ── 快速定位 FAB（当前播放所在分组存在时显示）
        floatingActionButton = {
            if (currentGroupIndex >= 0 && displayGroups.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = {
                        // 展开并定位到当前播放的分组
                        val group = displayGroups.getOrNull(currentGroupIndex)
                        locateGroupKey = group?.groupKey
                        locateHighlight.trigger(group?.groupKey)
                        coroutineScope.launch {
                            listState.animateScrollToItem(currentGroupIndex)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "定位到正在播放的歌曲",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        if (versionGroups.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                NoMultiVersionHint()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // ── 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "搜索歌曲名...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    )
                )

                // 搜索结果数量提示
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = if (displayGroups.isEmpty()) "无匹配结果"
                               else "找到 ${displayGroups.size} 组",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                    )
                }

                // ── 分组列表（LazyColumn，只渲染可见 item）
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 88.dp  // 留出 FAB 空间
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = displayGroups,
                        key = { _, group -> "vg_${group.groupKey}" }
                    ) { _, group ->
                        // forceExpanded: 当 locateGroupKey 匹配时强制展开此分组
                        val forceExpanded = group.groupKey == locateGroupKey

                        VersionGroupRow(
                            group = group,
                            currentSong = currentSong,
                            isPlaying = isPlaying,
                            forceExpanded = forceExpanded,
                            modifier = Modifier.locateHighlightFlash(group.groupKey, locateHighlight),
                            onPlayVersion = onPlayVersion,
                            onAddToQueue = onAddToQueue,
                            onShowInfo = { songForInfo = it },
                            onDeleteVersion = { songToDelete = it },
                            onCopyTitle = { title ->
                                onCopied(title)
                            },
                            onDetachVersion = onDetachVersion,
                            onReassignVersion = { songToReassign = it }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 删除版本确认对话框（从 VersionManagementDialog 迁移过来）
// ─────────────────────────────────────────────────────────────────

/**
 * 删除某个版本的确认对话框。
 *
 * 警告用户该操作将从本地文件和所有歌单中移除该歌曲，且不可撤销。
 *
 * @param song      要删除的歌曲（某个版本）
 * @param onDismiss 关闭回调
 * @param onConfirm 确认删除回调
 */
@Composable
fun DeleteVersionConfirmDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除该版本",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = "确定要删除以下歌曲版本吗？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "「${song.title}」",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (song.sampleRateDisplay.isNotEmpty()) {
                    Text(
                        text = "采样率：${song.sampleRateDisplay}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (song.artist.isNotEmpty()) {
                    Text(
                        text = "艺术家：${song.artist}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "该版本将从本地文件和所有歌单中移除。此操作不可撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("确认删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
