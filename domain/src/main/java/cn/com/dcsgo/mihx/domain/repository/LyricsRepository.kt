package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song

interface LyricsRepository {
    suspend fun lyricsFor(song: Song): Lyrics
}
