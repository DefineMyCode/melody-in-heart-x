package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

interface PlaylistRepository {
    fun getPlaylists(): List<Playlist>
    fun createPlaylist(name: String): Playlist?
    fun deletePlaylist(playlistId: Int): Boolean
    fun renamePlaylist(playlistId: Int, newName: String): Boolean
    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean
    fun removeSongFromPlaylist(playlistId: Int, songId: Int): Boolean
    fun getSongsByPlaylistId(playlistId: Int): List<Song>
    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean
}
