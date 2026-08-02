@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun PlaylistRoute(onOpenLibrary: () -> Unit = {}) {
    val viewModel: PlaylistViewModel = hiltViewModel()
    PlaylistScreen(viewModel = viewModel, onOpenLibrary = onOpenLibrary)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.playlistRoute() {
    composable("playlist") {
        PlaylistRoute()
    }
}
