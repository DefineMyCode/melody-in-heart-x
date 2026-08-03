package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo

interface SongMetadataRepository {
    suspend fun songInfo(song: Song): SongInfo?
}
