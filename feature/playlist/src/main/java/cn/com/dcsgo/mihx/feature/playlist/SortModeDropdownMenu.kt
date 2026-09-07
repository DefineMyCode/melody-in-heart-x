package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.domain.model.SongSortMode

/**
 * 本地音乐排序方式下拉菜单：上半区八种排序方式（当前项打勾），
 * 底部固定一条「切换升降序」项，带当前方向箭头。
 */
@Composable
fun SortModeDropdownMenu(
    expanded: Boolean,
    currentMode: SongSortMode,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onModeSelected: (SongSortMode) -> Unit,
    onDirectionToggled: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SongSortMode.entries.forEach { mode ->
            DropdownMenuItem(
                text = { Text(mode.label) },
                leadingIcon = {
                    if (mode == currentMode) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = { onModeSelected(mode) },
            )
        }
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (ascending) "升序（点按切换为降序）" else "降序（点按切换为升序）",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            onClick = onDirectionToggled,
        )
    }
}
