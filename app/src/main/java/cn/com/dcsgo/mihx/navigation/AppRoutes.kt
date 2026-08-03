package cn.com.dcsgo.mihx.navigation

object AppRoutes {
    const val HOME = "home"
    const val PLAYLIST = "playlist"
    const val PLAYLIST_ID = "playlistId"
    const val PLAYLIST_DETAIL = "playlist/{$PLAYLIST_ID}"
    const val USER = "user"
    const val VERSION_MANAGEMENT = "version-management"
    const val RAW_PLAY_STATS = "play-stats/raw"
    const val EFFECTIVE_PLAY_STATS = "play-stats/effective"
    const val QUICK_SKIP_SONGS = "quick-skip-songs"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"

    fun playlistDetail(playlistId: Int): String = "playlist/$playlistId"
}
