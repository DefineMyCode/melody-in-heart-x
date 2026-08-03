package cn.com.dcsgo.mihx.navigation

import cn.com.dcsgo.mihx.R

enum class AppDestinations(
    val route: String,
    val label: String,
    val iconResId: Int,
) {
    HOME(AppRoutes.HOME, "首页", R.drawable.ic_home_24),
    PLAYLIST(AppRoutes.PLAYLIST, "歌单", R.drawable.queue_music_24),
    USER(AppRoutes.USER, "我的", R.drawable.ic_person_24),
    ;

    companion object {
        fun fromRoute(route: String?): AppDestinations {
            return when {
                route == USER.route -> USER
                route == PLAYLIST.route || route?.startsWith("${PLAYLIST.route}/") == true -> PLAYLIST
                else -> HOME
            }
        }
    }
}
