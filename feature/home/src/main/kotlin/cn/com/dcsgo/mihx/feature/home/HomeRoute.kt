@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.home

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun HomeRoute() {
    val viewModel: HomeViewModel = hiltViewModel()
    HomeScreen(viewModel = viewModel)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.homeRoute() {
    composable("home") {
        HomeRoute()
    }
}
