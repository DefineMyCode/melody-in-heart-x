@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.player

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun PlayerRoute() {
    val viewModel: PlayerViewModel = hiltViewModel()
    PlayerScreen(viewModel = viewModel)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.playerRoute() {
    composable("player") {
        PlayerRoute()
    }
}
