@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.SongRow

@Composable
fun PlaylistScreen(viewModel: PlaylistViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedId = state.selectedPlaylistId
    if (selectedId != null) {
        PlaylistDetailScreen(state = state, viewModel = viewModel)
    } else {
        PlaylistListScreen(state = state, viewModel = viewModel)
    }

    when (val dialog = state.dialog) {
        PlaylistDialog.None -> {}
        PlaylistDialog.Create ->
            PlaylistNameDialog(
                title = "新建歌单",
                initial = "",
                onConfirm = viewModel::onCreateConfirm,
                onDismiss = viewModel::onDialogDismiss,
            )
        is PlaylistDialog.Rename ->
            PlaylistNameDialog(
                title = "重命名歌单",
                initial = dialog.currentName,
                onConfirm = viewModel::onRenameConfirm,
                onDismiss = viewModel::onDialogDismiss,
            )
    }
}

@Composable
private fun PlaylistListScreen(state: PlaylistUiState, viewModel: PlaylistViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("歌单") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onCreateClick) {
                Icon(Icons.Filled.Add, contentDescription = "新建歌单")
            }
        },
    ) { padding ->
        if (state.playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("还没有歌单，点击右下角新建")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistItemRow(
                        name = playlist.name,
                        onClick = { viewModel.onOpenPlaylist(playlist.id) },
                        onRename = { viewModel.onRenameClick(playlist.id) },
                        onDelete = { viewModel.onDeleteClick(playlist.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistItemRow(
    name: String,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = name, modifier = Modifier.weight(1f))
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("重命名") },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text("删除") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun PlaylistDetailScreen(state: PlaylistUiState, viewModel: PlaylistViewModel) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedPlaylist?.name ?: "歌单") },
                navigationIcon = {
                    IconButton(onClick = viewModel::onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.detailSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("歌单为空")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                // No stable key: playlists may contain the same song twice, so song.id would
                // collide inside the LazyColumn ("Key N was already used" crash). Position indexing
                // is safe here since SongRow carries no per-item state.
                itemsIndexed(state.detailSongs) { index, song ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SongRow(song = song, modifier = Modifier.weight(1f), onClick = { viewModel.onPlaySong(index) })
                        IconButton(onClick = { viewModel.onMove(index, (index - 1).coerceAtLeast(0)) }) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                        }
                        IconButton(
                            onClick = {
                                viewModel.onMove(index, (index + 1).coerceAtMost(state.detailSongs.lastIndex))
                            },
                        ) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                        }
                        IconButton(onClick = { viewModel.onRemoveSong(song.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "移除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("名称") },
            )
        },
    )
}
