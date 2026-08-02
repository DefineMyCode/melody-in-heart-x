@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cn.com.dcsgo.mihx.feature.home.HomeRoute
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRoute
import cn.com.dcsgo.mihx.feature.player.PlayerRoute
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRoute
import cn.com.dcsgo.mihx.feature.settings.SettingsRoute
import cn.com.dcsgo.mihx.feature.user.UserRoute

/** Bottom-navigation entry: destination + label + icon (icon-with-text, plan P5-UI). */
private data class NavItem(
    val destination: String,
    val label: String,
    val icon: ImageVector,
)

/** Only three tabs per user request: 曲库 / 播放 / 我的. 歌词 enters from Player, 设置 from 我的. */
private val navItems = listOf(
    NavItem(MelodyDestination.HOME, "曲库", Icons.Filled.LibraryMusic),
    NavItem(MelodyDestination.PLAYER, "播放", Icons.Filled.PlayCircle),
    NavItem(MelodyDestination.USER, "我的", Icons.Filled.Person),
)

@Composable
fun MelodyNavHost(
    navController: NavHostController = rememberNavController(),
) {
    var currentDestination by remember { mutableStateOf(MelodyDestination.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { item ->
                item(
                    selected = currentDestination == item.destination,
                    onClick = {
                        currentDestination = item.destination
                        navController.navigate(item.destination) {
                            popUpTo(MelodyDestination.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                )
            }
        },
    ) {
        androidx.navigation.compose.NavHost(
            navController = navController,
            startDestination = MelodyDestination.HOME,
            // P5-UI: keep page content below the status bar. NavigationSuiteScaffold's default
            // contentWindowInsets only covers Horizontal+Bottom, so screens without their own
            // TopAppBar inset handling (e.g. PlayerScreen) would bleed into the status bar.
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        ) {
            composable(MelodyDestination.HOME) { HomeRoute() }
            composable(MelodyDestination.PLAYLIST) { PlaylistRoute() }
            composable(MelodyDestination.PLAYER) {
                PlayerRoute(onOpenLyrics = { navController.navigate(MelodyDestination.LYRICS) })
            }
            composable(MelodyDestination.LYRICS) {
                LyricsRoute(onBack = { navController.navigateUp() })
            }
            composable(MelodyDestination.USER) {
                UserRoute(onOpenSettings = { navController.navigate(MelodyDestination.SETTINGS) })
            }
            composable(MelodyDestination.SETTINGS) { SettingsRoute() }
        }
    }
}
