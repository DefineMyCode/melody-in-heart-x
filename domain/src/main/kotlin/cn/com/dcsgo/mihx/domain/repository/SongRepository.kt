package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Song

interface SongRepository {
    suspend fun getAll(): List<Song>
    suspend fun getById(id: Long): Song?
}
