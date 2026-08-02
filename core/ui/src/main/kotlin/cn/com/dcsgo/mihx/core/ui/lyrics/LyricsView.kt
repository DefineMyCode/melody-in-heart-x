@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import cn.com.dcsgo.mihx.core.model.LyricLine

/**
 * Scrollable lyrics view; [activeIndex] is highlighted and kept in view as playback advances.
 * Tapping a line reports it via [onLineClick] (the host seeks playback to that timestamp).
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
                modifier = Modifier.fillMaxWidth().clickable { onLineClick(line) },
            )
        }
    }
}
