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
                // 我的页及其子页面（设置、播放统计、统计子页、歌曲 TOP 榜）
                route == USER.route ||
                    route == AppRoutes.SETTINGS ||
                    route == AppRoutes.PLAYBACK_STATS ||
                    route == AppRoutes.FILE_CHECK ||
                    route == AppRoutes.RAW_PLAY_STATS ||
                    route == AppRoutes.EFFECTIVE_PLAY_STATS ||
                    route == AppRoutes.SONG_TOP_LIST_FULL -> USER
                // 曲库页及其子页面（歌单详情、歌手/专辑详情、多版本管理、秒切歌曲）
                route == PLAYLIST.route ||
                    route?.startsWith("${PLAYLIST.route}/") == true ||
                    route?.startsWith("artist/") == true ||
                    route?.startsWith("album/") == true ||
                    route == AppRoutes.VERSION_MANAGEMENT ||
                    route?.startsWith("version-comparison/") == true ||
                    route == AppRoutes.QUICK_SKIP_SONGS -> PLAYLIST
                // 播放页（首页、歌词）及其它
                else -> HOME
            }
        }
    }
}
