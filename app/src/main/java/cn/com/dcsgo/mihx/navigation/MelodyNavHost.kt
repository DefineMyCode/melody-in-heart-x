@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.navigation

import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.com.dcsgo.mihx.feature.home.HomeRoute
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRoute
import cn.com.dcsgo.mihx.feature.player.PlayerRoute
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRoute
import cn.com.dcsgo.mihx.feature.settings.SettingsRoute
import cn.com.dcsgo.mihx.feature.user.UserRoute

@Composable
fun MelodyNavHost(
    navController: NavHostController = rememberNavController(),
) {
    var currentDestination by remember { mutableStateOf(MelodyDestination.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            MelodyDestination.entries.forEach { dest ->
                item(
                    selected = currentDestination == dest,
                    onClick = {
                        currentDestination = dest
                        navController.navigate(dest) {
                            popUpTo(MelodyDestination.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { },
                    label = { Text(dest) },
                )
            }
        },
    ) {
        androidx.navigation.compose.NavHost(
            navController = navController,
            startDestination = MelodyDestination.HOME,
        ) {
            composable(MelodyDestination.HOME) { HomeRoute() }
            composable(MelodyDestination.PLAYLIST) { PlaylistRoute() }
            composable(MelodyDestination.PLAYER) { PlayerRoute() }
            composable(MelodyDestination.LYRICS) { LyricsRoute() }
            composable(MelodyDestination.USER) { UserRoute() }
            composable(MelodyDestination.SETTINGS) { SettingsRoute() }
        }
    }
}
