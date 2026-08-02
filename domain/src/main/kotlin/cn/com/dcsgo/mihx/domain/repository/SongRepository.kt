package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.flow.Flow

interface SongRepository {
    /** Live, sorted library stream for the home list. */
    fun observeAll(): Flow<List<Song>>

    suspend fun getAll(): List<Song>
    suspend fun getById(id: Long): Song?
    suspend fun upsert(song: Song)
    suspend fun upsertAll(songs: List<Song>)
    suspend fun delete(id: Long)
}
