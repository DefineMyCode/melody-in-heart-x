@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.lyrics.LyricsView

@Composable
fun LyricsScreen(viewModel: LyricsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("歌词") }) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            val lyrics = state.lyrics
            when {
                lyrics == null ->
                    Text("暂无歌词", style = MaterialTheme.typography.bodyLarge)

                else ->
                    LyricsView(
                        lines = lyrics.lines,
                        activeIndex = state.activeIndex,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        onLineClick = viewModel::onLineClick,
                    )
            }
        }
    }
}
