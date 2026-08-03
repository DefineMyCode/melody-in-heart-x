package cn.com.dcsgo.mihx.domain.repository

interface AlbumArtRepository {
    suspend fun refreshAllAlbumArt()
}
