package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import cn.com.dcsgo.mihx.core.model.Song

// ─────────────────────────────────────────────────────────────────
// 版本重新关联对话框
// ─────────────────────────────────────────────────────────────────

/**
 * 将某个版本关联到另一首歌曲分组的对话框。
 *
 * 功能：
 * - 展示当前要重新关联的版本信息
 * - 支持搜索现有歌曲（按标题/艺术家）
 * - 单选选中目标歌曲
 * - 提供"复制歌曲名"快捷按钮，方便搜索
 * - 确认后将该版本的 groupKey 覆盖为目标歌曲的 groupKey
 *
 * @param song          待重新关联的版本歌曲
 * @param allSongs      所有可选的歌曲列表（用于搜索选择目标）
 * @param onDismiss     关闭对话框
 * @param onConfirm     确认关联——传入选定的目标歌曲
 * @param onCopied      复制歌曲名后的回调（用于显示 Toast）
 */
@Composable
fun ReassignVersionDialog(
    song: Song,
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: (targetSong: Song) -> Unit,
    onCopied: (text: String) -> Unit = {},
) {
    val context = LocalContext.current

    // 搜索文本
    var searchQuery by remember { mutableStateOf("") }

    // 当前选中的目标歌曲
    var selectedSong: Song? by remember { mutableStateOf(null) }

    // 过滤：排除自身，并根据搜索词过滤
    val filteredSongs by remember(allSongs, searchQuery, song) {
        derivedStateOf {
            val excludeId = song.id
            val query = searchQuery.trim()
            allSongs
                .filter { it.id != excludeId && it.uri != null }
                .let { list ->
                    if (query.isEmpty()) list
                    else list.filter {
                        it.title.contains(query, ignoreCase = true) ||
                            it.artist.contains(query, ignoreCase = true)
                    }
                }
                // 按 groupKey 去重（每个分组只展示一个代表），减少列表长度
                .distinctBy { it.groupKey }
                .sortedBy { it.title }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Column {
                Text(
                    text = "关联到其他歌曲",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 展示当前待关联的版本
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "「${song.title}」",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (song.sampleRateDisplay.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = song.sampleRateDisplay,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 快速复制歌曲名
                    IconButton(
                        onClick = {
                            copyToClipboard(context, song.title)
                            onCopied(song.title)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制歌曲名",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = "将关联到以下选中的歌曲分组中",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 搜索框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        selectedSong = null  // 搜索变化时清空选择
                    },
                    placeholder = {
                        Text(
                            text = "搜索歌曲名或艺术家...",
                            style = MaterialTheme.typography.bodySmall
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 结果数量提示
                Text(
                    text = if (filteredSongs.isEmpty()) "无匹配结果"
                           else "${filteredSongs.size} 首歌曲",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // 歌曲列表（最大高度限制避免撑满屏幕）
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = filteredSongs,
                        key = { "reassign_${it.id}" }
                    ) { target ->
                        ReassignTargetItem(
                            song = target,
                            isSelected = selectedSong?.id == target.id,
                            onClick = { selectedSong = target }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    selectedSong?.let { onConfirm(it) }
                },
                enabled = selectedSong != null
            ) {
                Text("确认关联")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

// ─────────────────────────────────────────────────────────────────
// 关联目标歌曲行
// ─────────────────────────────────────────────────────────────────

/**
 * 关联目标选择列表中的单个歌曲行。
 *
 * @param song       目标歌曲
 * @param isSelected 是否被选中
 * @param onClick    点击回调
 */
@Composable
private fun ReassignTargetItem(
    song: Song,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(song.artist.ifEmpty { "未知艺术家" })
                    if (song.sampleRateDisplay.isNotEmpty()) {
                        append(" · ")
                        append(song.sampleRateDisplay)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}
