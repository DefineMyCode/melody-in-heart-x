package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────
// 批量添加到歌单 Dialog
// ─────────────────────────────────────────────────────────────────

@Composable
fun BatchAddToPlaylistDialog(
    songs: List<Song>,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Playlist? = { _ -> null },
) {
    var showCreateInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(playlists.isEmpty()) {
        if (playlists.isNotEmpty()) showCreateInput = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            Column {
                Text(
                    text = "将 ${songs.size} 首歌曲添加到：",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                if (playlists.isEmpty() && !showCreateInput) {
                    Text(
                        text = "还没有歌单，创建一个吧~",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (playlists.isNotEmpty() && !showCreateInput) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(playlists, key = { "batch_dlg_${it.id}" }) { playlist ->
                            val alreadyInCount = songs.count { it.id in playlist.songIds }
                            val allAlreadyIn = alreadyInCount == songs.size

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (allAlreadyIn) Modifier
                                        else Modifier
                                            .clickable(enabled = !allAlreadyIn) { onSelectPlaylist(playlist) }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append("${playlist.songCount} 首歌曲")
                                            if (alreadyInCount > 0 && !allAlreadyIn) {
                                                append(" · ${songs.size - alreadyInCount} 首待添加")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (allAlreadyIn) {
                                    Text(
                                        text = "全部已存在",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else if (alreadyInCount > 0) {
                                    Text(
                                        text = "部分已存在",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.list_alt_add_24),
                                        contentDescription = "添加",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                PlaylistCreateInlineSection(
                    showCreateInput = showCreateInput,
                    newPlaylistName = newPlaylistName,
                    onShowCreateChange = { showCreateInput = it },
                    onNameChange = { newPlaylistName = it },
                    onCreate = {
                        val name = newPlaylistName.trim()
                        if (name.isNotBlank()) {
                            val newPlaylist = onCreatePlaylist(name)
                            if (newPlaylist != null) {
                                onSelectPlaylist(newPlaylist)
                            }
                            newPlaylistName = ""
                            showCreateInput = false
                        }
                    },
                    onCancel = {
                        showCreateInput = false
                        newPlaylistName = ""
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────
// 单首歌曲添加到歌单 Dialog
// ─────────────────────────────────────────────────────────────────

@Composable
fun SingleSongAddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Playlist? = { _ -> null },
) {
    var showCreateInput by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(playlists.isEmpty()) {
        if (playlists.isNotEmpty()) showCreateInput = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到歌单") },
        text = {
            Column {
                Text(
                    text = "将「${song.title}」添加到：",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                if (playlists.isEmpty() && !showCreateInput) {
                    Text(
                        text = "还没有歌单，创建一个吧~",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (playlists.isNotEmpty() && !showCreateInput) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(playlists, key = { "single_dlg_${it.id}" }) { playlist ->
                            val alreadyIn = song.id in playlist.songIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (alreadyIn) Modifier
                                        else Modifier
                                            .clickable(enabled = !alreadyIn) { onSelectPlaylist(playlist) }
                                    )
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.songCount} 首歌曲",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (alreadyIn) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "已存在",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.list_alt_add_24),
                                        contentDescription = "添加",
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                PlaylistCreateInlineSection(
                    showCreateInput = showCreateInput,
                    newPlaylistName = newPlaylistName,
                    onShowCreateChange = { showCreateInput = it },
                    onNameChange = { newPlaylistName = it },
                    onCreate = {
                        val name = newPlaylistName.trim()
                        if (name.isNotBlank()) {
                            val newPlaylist = onCreatePlaylist(name)
                            if (newPlaylist != null) {
                                onSelectPlaylist(newPlaylist)
                            }
                            newPlaylistName = ""
                            showCreateInput = false
                        }
                    },
                    onCancel = {
                        showCreateInput = false
                        newPlaylistName = ""
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────
// 歌曲信息 Dialog
// ─────────────────────────────────────────────────────────────────

@Composable
fun SongInfoDialog(
    song: Song,
    songInfo: SongInfo,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "歌曲信息",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song.albumArtUri != null) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = "专辑封面",
                                modifier = Modifier.size(56.dp),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                InfoRow(label = "专辑", value = songInfo.album)
                InfoRow(label = "时长", value = songInfo.duration)
                InfoRow(label = "格式", value = songInfo.format)
                InfoRow(label = "比特率", value = songInfo.bitRate)
                InfoRow(label = "采样率", value = songInfo.sampleRate)
                InfoRow(label = "文件大小", value = songInfo.fileSize)
                InfoRow(label = "文件路径", value = songInfo.filePath, isPath = true)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String, isPath: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = if (isPath) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (isPath) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 删除歌曲确认 Dialog
// ─────────────────────────────────────────────────────────────────

@Composable
fun DeleteSongConfirmDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除歌曲",
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = "确定要删除「${song.title}」吗？",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "歌曲将从所有歌单中移除，本地文件也会被删除。此操作不可撤销。",
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
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ─────────────────────────────────────────────────────────────────
// 共享的内联新建歌单区域
// ─────────────────────────────────────────────────────────────────

@Composable
private fun PlaylistCreateInlineSection(
    showCreateInput: Boolean,
    newPlaylistName: String,
    onShowCreateChange: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit,
) {
    if (showCreateInput) {
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = newPlaylistName,
            onValueChange = onNameChange,
            placeholder = { Text("歌单名称", style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            FilledTonalButton(
                onClick = onCreate,
                enabled = newPlaylistName.trim().isNotBlank()
            ) {
                Text("创建并添加")
            }
        }
    } else {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = { onShowCreateChange(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "新建歌单",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
