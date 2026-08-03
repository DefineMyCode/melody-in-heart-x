package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable

data class UserRouteState(
    val placeholders: Unit = Unit,
)

data class UserRouteActions(
    val onShowSettings: () -> Unit,
    val onShowRawPlayStats: () -> Unit,
    val onShowEffectivePlayStats: () -> Unit,
)

@Composable
fun UserRoute(
    state: UserRouteState,
    actions: UserRouteActions,
) {
    UserScreen(
        onShowSettings = actions.onShowSettings,
        onShowPlayStats = actions.onShowRawPlayStats,
        onShowEffectivePlayStats = actions.onShowEffectivePlayStats,
    )
}
