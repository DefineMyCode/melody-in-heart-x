package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import javax.inject.Inject

class PlaylistRepositoryAdapter @Inject constructor(
    private val musicRepository: MusicRepository,
) : PlaylistRepository {
    override fun getPlaylists(): List<Playlist> = musicRepository.getPlaylists()

    override fun createPlaylist(name: String): Playlist = musicRepository.createPlaylist(name)

    override fun deletePlaylist(playlistId: Int): Boolean = musicRepository.deletePlaylist(playlistId)

    override fun renamePlaylist(playlistId: Int, newName: String): Boolean =
        musicRepository.renamePlaylist(playlistId, newName)

    override fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean =
        musicRepository.addSongToPlaylist(playlistId, songId)

    override fun removeSongFromPlaylist(playlistId: Int, songId: Int): Boolean =
        musicRepository.removeSongFromPlaylist(playlistId, songId)

    override fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>): Boolean =
        musicRepository.reorderPlaylist(playlistId, orderedSongIds)

    override fun getSongsByPlaylistId(playlistId: Int): List<Song> =
        musicRepository.getSongsByPlaylistId(playlistId)

    override fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean =
        musicRepository.isSongInPlaylist(playlistId, songId)
}
