@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.EmptyState
import cn.com.dcsgo.mihx.core.ui.component.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val folderLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.importTree(it.toString()) } }

    val filesLauncher = rememberLauncherForActivityResult(OpenAudioDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris.map { it.toString() })
    }

    val filtered = remember(state.songs, state.query) {
        if (state.query.isBlank()) {
            state.songs
        } else {
            val q = state.query.lowercase()
            state.songs.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("曲库") },
                actions = {
                    if (state.selectedIds.isNotEmpty()) {
                        IconButton(onClick = viewModel::openAddToPlaylistDialog) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "加入歌单")
                        }
                        IconButton(onClick = viewModel::addSelectedToQueue) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = "加入队列")
                        }
                        IconButton(onClick = viewModel::deleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除选中")
                        }
                    }
                    IconButton(onClick = { filesLauncher.launch(Unit) }) {
                        Icon(Icons.Filled.Add, contentDescription = "导入文件")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { folderLauncher.launch(null) }) {
                Icon(Icons.Filled.Folder, contentDescription = "导入文件夹")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("搜索曲名 / 艺人") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(8.dp),
            )
            if (state.isImporting) {
                val progress = state.importProgress
                val fraction = if (progress == null || progress.total == 0) 0f else progress.done.toFloat() / progress.total
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
            }
            if (filtered.isEmpty()) {
                EmptyState(
                    if (state.songs.isEmpty()) "曲库为空，点击右下角导入音乐文件夹" else "无匹配结果",
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            selected = song.id in state.selectedIds,
                            selectable = true,
                            showCover = false,
                            onClick = { viewModel.toggleSelect(song.id) },
                        )
                    }
                }
            }
        }
    }

    if (state.showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAddToPlaylistDialog,
            title = { Text("加入歌单") },
            text = {
                if (state.playlists.isEmpty()) {
                    Text("还没有歌单，请先在「歌单」页创建")
                } else {
                    LazyColumn {
                        items(state.playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.addSelectedToPlaylist(playlist.id) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(playlist.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissAddToPlaylistDialog) { Text("取消") }
            },
        )
    }
}
