package cn.com.dcsgo.mihx.navigation

import cn.com.dcsgo.mihx.R

enum class AppDestinations(
    val route: String,
    val label: String,
    val iconResId: Int,
) {
    PLAYLIST(AppRoutes.PLAYLIST, "曲库", R.drawable.queue_music_24),
    HOME(AppRoutes.HOME, "播放", R.drawable.ic_play),
    USER(AppRoutes.USER, "我的", R.drawable.ic_person_24),
    ;

    companion object {
        fun fromRoute(route: String?): AppDestinations {
            return when {
                route == USER.route -> USER
                route == PLAYLIST.route ||
                    route?.startsWith("${PLAYLIST.route}/") == true ||
                    route?.startsWith("artist/") == true ||
                    route?.startsWith("album/") == true -> PLAYLIST
                else -> HOME
            }
        }
    }
}
