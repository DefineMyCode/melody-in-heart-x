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
    const val RAW_PLAY_STATS = "play-stats/raw"
    const val EFFECTIVE_PLAY_STATS = "play-stats/effective"
    const val QUICK_SKIP_SONGS = "quick-skip-songs"
    const val SETTINGS = "settings"
    const val LYRICS = "lyrics"

    fun playlistDetail(playlistId: Int): String = "playlist/$playlistId"

    fun artistDetail(artistName: String): String = "artist/${Uri.encode(artistName)}"

    fun albumDetail(albumName: String): String = "album/${Uri.encode(albumName)}"
}
