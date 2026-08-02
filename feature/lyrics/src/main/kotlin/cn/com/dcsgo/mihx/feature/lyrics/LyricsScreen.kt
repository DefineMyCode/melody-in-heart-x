@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.EmptyState
import cn.com.dcsgo.mihx.core.ui.lyrics.LyricsView

@Composable
fun LyricsScreen(viewModel: LyricsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("歌词") }) },
    ) { padding ->
        val lyrics = state.lyrics
        if (lyrics == null) {
            EmptyState("暂无歌词", Modifier.padding(padding))
        } else {
            LyricsView(
                lines = lyrics.lines,
                activeIndex = state.activeIndex,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                onLineClick = viewModel::onLineClick,
            )
        }
    }
}
