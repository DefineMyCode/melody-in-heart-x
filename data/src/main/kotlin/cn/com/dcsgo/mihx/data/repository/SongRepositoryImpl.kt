package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.mapper.toSong
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : SongRepository {
    override suspend fun getAll(): List<Song> = dao.getAllSongs().map { it.toSong() }

    override suspend fun getById(id: Long): Song? = dao.getSongById(id)?.toSong()
}
