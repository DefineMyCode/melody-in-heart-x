package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.database.entity.PlaylistSongCrossRefEntity
import cn.com.dcsgo.mihx.data.mapper.toSong
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : PlaylistRepository {

    override fun observeAll(): Flow<List<Playlist>> =
        dao.observePlaylists().map { list -> list.map { Playlist(id = it.id, name = it.name, createdAt = it.createdAt) } }

    override suspend fun getById(id: Long): Playlist? =
        dao.getPlaylistById(id)?.let { Playlist(id = it.id, name = it.name, createdAt = it.createdAt) }

    override suspend fun create(name: String): Long =
        dao.upsertPlaylist(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))

    override suspend fun rename(id: Long, name: String) = dao.renamePlaylist(id, name)

    override suspend fun delete(id: Long) {
        dao.deletePlaylistSongs(id)
        dao.deletePlaylist(id)
    }

    override suspend fun getSongs(playlistId: Long): List<Song> =
        dao.getPlaylistSongEntities(playlistId).map { it.toSong() }

    override suspend fun addSong(playlistId: Long, songId: Long) {
        val position = dao.getPlaylistSongs(playlistId).size
        dao.upsertPlaylistSong(PlaylistSongCrossRefEntity(playlistId, songId, position))
    }

    override suspend fun removeSong(playlistId: Long, songId: Long) =
        dao.deletePlaylistSong(playlistId, songId)

    override suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) {
        val refs = orderedSongIds.mapIndexed { index, songId ->
            PlaylistSongCrossRefEntity(playlistId, songId, index)
        }
        dao.upsertPlaylistSongs(refs)
    }
}
