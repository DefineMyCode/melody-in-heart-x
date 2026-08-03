package cn.com.dcsgo.mihx.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.player.MusicPlayerBottomBar
import cn.com.dcsgo.mihx.navigation.AppDestinations

@Composable
fun AppScaffold(
    currentDestination: AppDestinations,
    currentSong: Song?,
    isPlaying: Boolean,
    onDestinationSelected: (AppDestinations) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onNavigateToHome: () -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            painter = painterResource(destination.iconResId),
                            contentDescription = destination.label,
                        )
                    },
                    label = { Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { onDestinationSelected(destination) },
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            topBar()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                content()
            }
            if (currentDestination != AppDestinations.HOME && currentSong != null) {
                MusicPlayerBottomBar(
                    isPlaying = isPlaying,
                    currentSong = currentSong,
                    onPlayPauseClick = onPlayPauseClick,
                    onPreviousClick = onPreviousClick,
                    onNextClick = onNextClick,
                    onNavigateToHome = onNavigateToHome,
                )
            }
        }
    }
}
