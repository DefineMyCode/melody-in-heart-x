@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.LyricLine

/**
 * Scrollable lyrics view; [activeIndex] is highlighted and kept in view as playback advances.
 * Tapping a line reports it via [onLineClick] (the host seeks playback to that timestamp).
 *
 * UI 设计定稿：当前行以 primary 最强前景 + titleMedium 强调，其余行弱化为
 * onSurfaceVariant + bodyMedium；行距 8dp 纵向呼吸。
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onLineClick: (LyricLine) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            listState.animateScrollToItem(activeIndex)
        }
    }
    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            Text(
                text = line.text,
                textAlign = TextAlign.Center,
                style = if (isActive) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}
