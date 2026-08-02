package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.mapper.toEntity
import cn.com.dcsgo.mihx.data.mapper.toSong
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : SongRepository {
    override fun observeAll(): Flow<List<Song>> = dao.observeSongs().map { list -> list.map { it.toSong() } }

    override suspend fun getAll(): List<Song> = dao.getAllSongs().map { it.toSong() }

    override suspend fun getById(id: Long): Song? = dao.getSongById(id)?.toSong()

    override suspend fun upsert(song: Song) {
        dao.upsertSong(song.toEntity())
    }

    override suspend fun upsertAll(songs: List<Song>) {
        songs.forEach { dao.upsertSong(it.toEntity()) }
    }

    override suspend fun delete(id: Long) {
        dao.deleteSong(id)
    }
}
