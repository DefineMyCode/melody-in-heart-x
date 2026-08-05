package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────
// 歌曲列表操作栏（标题 + 多选 / 搜索入口，多选时显示全选 + 计数）
// ─────────────────────────────────────────────────────────────────

/**
 * 歌曲列表通用操作栏
 *
 * 第一行：标题 + 计数 + 多选切换图标 + 搜索切换图标；
 * 多选模式下追加第二行：全选 / 取消全选 + 「已选 N 首」。
 *
 * 供本地音乐页、歌单详情页、歌手详情页、专辑详情页共用。
 *
 * @param title        列表标题（如「本地音乐」「歌曲列表」「歌曲」）
 * @param totalCount   完整列表数量（搜索时用于展示总数）
 * @param displayCount 当前过滤后显示的数量
 * @param canSelect    是否允许多选（空列表时隐藏多选入口）
 */
@Composable
fun SongListActionBar(
    title: String,
    totalCount: Int,
    displayCount: Int,
    isSearching: Boolean,
    isSelectMode: Boolean,
    isAllSelected: Boolean,
    selectedCount: Int,
    canSelect: Boolean = true,
    onToggleSearch: () -> Unit,
    onToggleSelectMode: () -> Unit,
    onSelectAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isSearching) "$displayCount / $totalCount" else "$totalCount 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))

            if (canSelect) {
                IconButton(
                    onClick = onToggleSelectMode,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.list_alt_add_24),
                        contentDescription = if (isSelectMode) "取消多选" else "多选",
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelectMode) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearching) "关闭搜索" else "搜索",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (isSelectMode) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onSelectAll) {
                Icon(
                    imageVector = Icons.Default.DoneAll,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAllSelected) "取消全选" else "全选",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "已选 $selectedCount 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 歌曲搜索框
// ─────────────────────────────────────────────────────────────────

/** 歌曲列表页内搜索框（实时过滤歌曲名 / 歌手） */
@Composable
fun SongSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索歌曲名或艺术家",
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* 已实时过滤 */ }),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
        )
    )
}
