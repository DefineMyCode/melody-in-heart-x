package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Playlist

interface PlaylistRepository {
    suspend fun getAll(): List<Playlist>
}
