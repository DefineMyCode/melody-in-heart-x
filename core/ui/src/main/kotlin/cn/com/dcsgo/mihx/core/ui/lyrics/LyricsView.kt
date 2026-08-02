@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
 * Scrollable lyrics view; the active line is highlighted and kept vertically centered as playback
 * advances. Tapping a line reports it via [onLineClick] (the host seeks playback to that timestamp).
 *
 * Centering is done the classic way: top/bottom content padding of half the viewport height means
 * every line (including the first and last) can scroll to the exact middle of the visible area —
 * no manual scroll-offset math, and lines never end up hidden behind the status bar / app bar /
 * bottom navigation.
 *
 * UI 设计定稿：当前行以 primary 最强前景 + titleLarge 强调，其余行弱化为
 * onSurfaceVariant + bodyMedium；行距 8dp 纵向呼吸。
 */
@Composable
fun LyricsView(
    lines: List<LyricLine>,
    activeIndex: Int,
    modifier: Modifier = Modifier,
    onLineClick: (LyricLine) -> Unit = {},
    fontScale: Float = 1f,
) {
    val listState = rememberLazyListState()
    // 字号随 [fontScale] 整体缩放：当前行与普通行同比例（UI 定稿：A+/A− 按钮）。
    val activeStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = MaterialTheme.typography.titleLarge.fontSize * fontScale,
    )
    val inactiveStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = MaterialTheme.typography.bodyMedium.fontSize * fontScale,
    )
    BoxWithConstraints(modifier = modifier) {
        // 真实可视区（已扣除标题栏/底部导航等外部占位）的一半，作为首尾留白。
        val halfViewport = maxHeight / 2
        LaunchedEffect(activeIndex) {
            if (activeIndex in lines.indices) {
                // 无动画：所有动画已全局禁用，直接跳到当前行。
                listState.scrollToItem(activeIndex)
            }
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = halfViewport),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text = line.text,
                    textAlign = TextAlign.Center,
                    style = if (isActive) activeStyle else inactiveStyle,
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
}
