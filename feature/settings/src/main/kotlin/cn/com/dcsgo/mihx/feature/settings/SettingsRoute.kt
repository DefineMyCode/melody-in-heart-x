@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun SettingsRoute() {
    val viewModel: SettingsViewModel = hiltViewModel()
    SettingsScreen(viewModel = viewModel)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.settingsRoute() {
    composable("settings") {
        SettingsRoute()
    }
}
