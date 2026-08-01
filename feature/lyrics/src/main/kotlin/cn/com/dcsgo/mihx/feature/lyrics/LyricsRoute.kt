@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.lyrics

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun LyricsRoute() {
    val viewModel: LyricsViewModel = hiltViewModel()
    LyricsScreen(viewModel = viewModel)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.lyricsRoute() {
    composable("lyrics") {
        LyricsRoute()
    }
}
