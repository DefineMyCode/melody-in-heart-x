package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import javax.inject.Inject

class SongRepositoryAdapter @Inject constructor(
    private val musicRepository: MusicRepository,
) : SongRepository {
    override suspend fun loadSongs(): List<Song> = musicRepository.loadSongs()

    override suspend fun countSongs(): Int = musicRepository.countSongs()

    override fun observeSongsSnapshot(): List<Song> = musicRepository.observeSongsSnapshot()

    override fun setSongsChangedListener(listener: (() -> Unit)?) {
        musicRepository.setSongsChangedListener(listener)
    }

    override suspend fun loadLibraryArtists(): List<ArtistEntry> = musicRepository.loadLibraryArtists()

    override suspend fun loadLibraryAlbums(): List<AlbumEntry> = musicRepository.loadLibraryAlbums()

    override fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean =
        musicRepository.updateSongTitleOverride(songId, titleOverride)

    override suspend fun deleteSong(songId: Int): DeleteSongResult = musicRepository.deleteSong(songId)

    override suspend fun validateAndCleanupLocalFiles(): LocalFileValidationResult =
        musicRepository.validateAndCleanupLocalFiles()
}
