package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.domain.repository.AlbumArtRepository
import javax.inject.Inject

class AlbumArtRepositoryAdapter @Inject constructor(
    private val musicRepository: MusicRepository,
) : AlbumArtRepository {
    override suspend fun refreshAllAlbumArt() {
        musicRepository.refreshAllAlbumArt()
    }
}
