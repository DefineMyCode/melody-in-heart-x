package cn.com.dcsgo.mihx.domain.playlist

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

interface PlaylistActions {
    fun getSongsByPlaylist(playlist: Playlist, songs: List<Song>): List<Song>
    fun createPlaylist(name: String): Playlist?
    fun deletePlaylist(playlistId: Int)
    fun renamePlaylist(playlistId: Int, newName: String): Boolean
    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean
    fun removeSongFromPlaylist(playlistId: Int, songId: Int)
    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean
    fun snapshot(): PlaylistSnapshot
}

data class PlaylistSnapshot(
    val songs: List<Song>,
    val playlists: List<Playlist>,
)
