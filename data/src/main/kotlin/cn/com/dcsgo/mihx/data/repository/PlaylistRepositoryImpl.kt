package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : PlaylistRepository {
    // Model Playlist is metadata-only today; song membership is read on demand via the cross-ref
    // table when playlist playback lands (later phase). Ordered by recency.
    override suspend fun getAll(): List<Playlist> =
        dao.getPlaylists().map { Playlist(id = it.id, name = it.name, createdAt = it.createdAt) }
}
