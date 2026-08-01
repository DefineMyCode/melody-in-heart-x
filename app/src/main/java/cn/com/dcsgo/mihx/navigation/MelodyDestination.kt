package cn.com.dcsgo.mihx.navigation

/**
 * Top-level navigation destinations. Each feature module exposes a matching
 * composable route registered in [MelodyNavHost].
 */
object MelodyDestination {
    const val HOME = "home"
    const val PLAYLIST = "playlist"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
    const val USER = "user"
    const val SETTINGS = "settings"

    val entries: List<String> = listOf(HOME, PLAYLIST, PLAYER, LYRICS, USER, SETTINGS)
}
