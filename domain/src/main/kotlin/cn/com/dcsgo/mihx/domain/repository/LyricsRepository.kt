package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song

/** Loads timed lyrics for a song. Implemented in `:data` (needs the Android content APIs). */
interface LyricsRepository {
    /** Loads lyrics for [song], or null when none are available. */
    suspend fun loadForSong(song: Song): Lyrics?
}
