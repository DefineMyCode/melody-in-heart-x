package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Lyrics

interface LyricsRepository {
    suspend fun getLyrics(songId: Long): Lyrics?
}
