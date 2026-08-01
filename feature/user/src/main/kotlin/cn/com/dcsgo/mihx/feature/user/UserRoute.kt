@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Top-level route composable registered by the app nav host. */
@Composable
fun UserRoute() {
    val viewModel: UserViewModel = hiltViewModel()
    UserScreen(viewModel = viewModel)
}

/** Nav-graph extension (alternative registration style). */
fun NavGraphBuilder.userRoute() {
    composable("user") {
        UserRoute()
    }
}
