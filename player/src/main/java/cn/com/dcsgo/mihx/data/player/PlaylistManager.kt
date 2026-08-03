package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playlist.PlaylistActions
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository

class PlaylistManager(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
) : PlaylistActions {
    override fun getSongsByPlaylist(playlist: Playlist, songs: List<Song>): List<Song> =
        Companion.getSongsByPlaylist(playlist, songs)

    override fun createPlaylist(name: String): Playlist? = playlistRepository.createPlaylist(name)

    override fun deletePlaylist(playlistId: Int) {
        playlistRepository.deletePlaylist(playlistId)
    }

    override fun renamePlaylist(playlistId: Int, newName: String): Boolean {
        return playlistRepository.renamePlaylist(playlistId, newName)
    }

    override fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistRepository.addSongToPlaylist(playlistId, songId)
    }

    override fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        playlistRepository.removeSongFromPlaylist(playlistId, songId)
    }

    override fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistRepository.isSongInPlaylist(playlistId, songId)
    }

    override fun snapshot(): PlaylistSnapshot {
        return PlaylistSnapshot(
            songs = songRepository.observeSongsSnapshot(),
            playlists = playlistRepository.getPlaylists(),
        )
    }

    companion object {
        fun getSongsByPlaylist(playlist: Playlist, songs: List<Song>): List<Song> {
            val songIds = playlist.songIds.toSet()
            return songs.filter { it.id in songIds }
        }
    }
}
