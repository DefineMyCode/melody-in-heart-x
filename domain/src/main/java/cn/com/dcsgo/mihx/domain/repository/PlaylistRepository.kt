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

    /**
     * 一次性整体替换歌单内容（单次写锁 + 单次落盘）。
     * m-9（评审 2026-10-15）：秒切同步等批量场景不再逐首增删（每步一次整表落盘、
     * 中间态短暂为空）。songIds 为最终顺序；歌单不存在返回 false。
     */
    fun replacePlaylistSongs(playlistId: Int, songIds: List<Int>): Boolean

    fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>): Boolean
    fun getSongsByPlaylistId(playlistId: Int): List<Song>
    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean
}
