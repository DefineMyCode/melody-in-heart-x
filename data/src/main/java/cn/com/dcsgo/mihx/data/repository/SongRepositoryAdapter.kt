package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import javax.inject.Inject

class SongRepositoryAdapter @Inject constructor(
    private val musicRepository: MusicRepository,
) : SongRepository {
    override suspend fun loadSongs(): List<Song> = musicRepository.loadSongs()

    override fun observeSongsSnapshot(): List<Song> = musicRepository.observeSongsSnapshot()

    override fun setSongsChangedListener(listener: (() -> Unit)?) {
        musicRepository.setSongsChangedListener(listener)
    }

    override fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean =
        musicRepository.updateSongTitleOverride(songId, titleOverride)

    override fun deleteSong(songId: Int): DeleteSongResult = musicRepository.deleteSong(songId)
}
