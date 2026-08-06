package cn.com.dcsgo.mihx.navigation

import android.net.Uri

object AppRoutes {
    const val HOME = "home"
    const val PLAYLIST = "playlist"
    const val PLAYLIST_ID = "playlistId"
    const val PLAYLIST_DETAIL = "playlist/{$PLAYLIST_ID}"
    const val ARTIST_NAME = "artistName"
    const val ARTIST_DETAIL = "artist/{$ARTIST_NAME}"
    const val ALBUM_NAME = "albumName"
    const val ALBUM_DETAIL = "album/{$ALBUM_NAME}"
    const val USER = "user"
    const val VERSION_MANAGEMENT = "version-management"
    const val VERSION_GROUP_ID = "groupId"
    const val VERSION_COMPARISON = "version-comparison/{$VERSION_GROUP_ID}"
    const val PLAYBACK_STATS = "playback-stats"
    const val SONG_TOP_LIST = "song-top-list"
    const val SONG_TOP_PERIOD = "period"
    const val SONG_TOP_LIST_FULL = "song-top-list?period={$SONG_TOP_PERIOD}"

    /** 歌曲 TOP 榜路由，period 取 "week" / "month" */
    fun songTopList(period: String): String = "song-top-list?period=$period"
    const val RAW_PLAY_STATS = "play-stats/raw"
    const val EFFECTIVE_PLAY_STATS = "play-stats/effective"
    const val QUICK_SKIP_SONGS = "quick-skip-songs"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"

    fun playlistDetail(playlistId: Int): String = "playlist/$playlistId"

    fun artistDetail(artistName: String): String = "artist/${Uri.encode(artistName)}"

    fun albumDetail(albumName: String): String = "album/${Uri.encode(albumName)}"

    fun versionComparison(groupId: String): String = "version-comparison/${Uri.encode(groupId)}"
}
