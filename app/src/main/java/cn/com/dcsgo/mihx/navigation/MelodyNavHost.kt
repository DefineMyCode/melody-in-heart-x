@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
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

/** Three tabs: 歌单 / 播放 / 我的. 曲库(Home) enters from the playlist screen, 歌词 from Player. */
private val navItems = listOf(
    NavItem(MelodyDestination.PLAYLIST, "歌单", Icons.Filled.PlaylistPlay),
    NavItem(MelodyDestination.PLAYER, "播放", Icons.Filled.PlayCircle),
    NavItem(MelodyDestination.USER, "我的", Icons.Filled.Person),
)

@Composable
fun MelodyNavHost(
    navController: NavHostController = rememberNavController(),
) {
    var currentDestination by remember { mutableStateOf(MelodyDestination.PLAYLIST) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { item ->
                item(
                    selected = currentDestination == item.destination,
                    onClick = {
                        currentDestination = item.destination
                        navController.navigate(item.destination) {
                            popUpTo(MelodyDestination.PLAYLIST) { saveState = true }
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
            startDestination = MelodyDestination.PLAYLIST,
            // 所有动画已全局禁用：页面切换无淡入淡出（Navigation Compose 默认 700ms fade）。
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
            // P5-UI: keep page content below the status bar. NavigationSuiteScaffold's default
            // contentWindowInsets only covers Horizontal+Bottom, so screens without their own
            // TopAppBar inset handling (e.g. PlayerScreen) would bleed into the status bar.
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        ) {
            composable(MelodyDestination.HOME) { HomeRoute() }
            composable(MelodyDestination.PLAYLIST) {
                PlaylistRoute(onOpenLibrary = { navController.navigate(MelodyDestination.HOME) })
            }
            composable(MelodyDestination.PLAYER) {
                PlayerRoute(
                    onOpenLyrics = {
                        // launchSingleTop: 快速连点封面不会压入多个歌词页实例——否则返回一次后
                        // 仍在歌词页（用户误以为已回播放页），点到歌词行会误触发 seek 改进度。
                        navController.navigate(MelodyDestination.LYRICS) { launchSingleTop = true }
                    },
                )
            }
            composable(MelodyDestination.LYRICS) {
                LyricsRoute(onBack = { navController.navigateUp() })
            }
            composable(MelodyDestination.USER) {
                UserRoute(
                    onOpenSettings = {
                        navController.navigate(MelodyDestination.SETTINGS) { launchSingleTop = true }
                    },
                )
            }
            composable(MelodyDestination.SETTINGS) { SettingsRoute() }
        }
    }
}
