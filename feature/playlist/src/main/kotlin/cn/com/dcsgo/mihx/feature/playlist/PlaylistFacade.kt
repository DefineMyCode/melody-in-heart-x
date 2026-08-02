package cn.com.dcsgo.mihx.feature.playlist

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.PlaylistWithCover
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Thin boundary exposing playlist use-cases to the ViewModel (concrete; injects the domain repo). */
class PlaylistFacade @Inject constructor(
    private val playlistRepository: PlaylistRepository,
) {
    fun observePlaylists(): Flow<List<PlaylistWithCover>> = playlistRepository.observeAllWithCover()
    suspend fun createPlaylist(name: String): Long = playlistRepository.create(name)
    suspend fun renamePlaylist(id: Long, name: String) = playlistRepository.rename(id, name)
    suspend fun deletePlaylist(id: Long) = playlistRepository.delete(id)
    suspend fun getSongs(playlistId: Long): List<Song> = playlistRepository.getSongs(playlistId)
    suspend fun addSong(playlistId: Long, songId: Long) = playlistRepository.addSong(playlistId, songId)
    suspend fun removeSong(playlistId: Long, songId: Long) = playlistRepository.removeSong(playlistId, songId)
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>) =
        playlistRepository.reorder(playlistId, orderedSongIds)
}
