@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalMaterial3Api::class)

package cn.com.dcsgo.mihx.feature.lyrics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.EmptyState
import cn.com.dcsgo.mihx.core.ui.component.MelodyTopAppBar
import cn.com.dcsgo.mihx.core.ui.lyrics.LyricsView

@Composable
fun LyricsScreen(viewModel: LyricsViewModel, onBack: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()

    // 返回流程标记：从按下返回开始（导航退出动画期间歌词页仍在组合、仍可点击），屏蔽所有
    // 歌词行点击。否则用户"返回后立刻点封面"会落在退出动画中的歌词行上，误触发 seek——
    // 且该点击被歌词页消费，封面导航不生效（正是用户复现的现象）。
    var leaving by remember { mutableStateOf(false) }
    val requestBack = {
        leaving = true
        onBack()
    }
    BackHandler(onBack = requestBack)

    Scaffold(
        // 显式 systemBars：歌词内容完整避开系统状态栏 / 导航手势区，不被任何栏遮挡。
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            MelodyTopAppBar(
                title = { Text("歌词") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 歌词字号整体缩放（当前行 + 其他行），进程内记住；中间 A 恢复默认。
                actions = {
                    TextButton(onClick = viewModel::shrinkFont) { Text("A−") }
                    TextButton(onClick = viewModel::resetFont) { Text("A") }
                    TextButton(onClick = viewModel::enlargeFont) { Text("A+") }
                },
            )
        },
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
                onLineClick = { line -> if (!leaving) viewModel.onLineClick(line) },
                fontScale = fontScale,
            )
        }
    }
}
