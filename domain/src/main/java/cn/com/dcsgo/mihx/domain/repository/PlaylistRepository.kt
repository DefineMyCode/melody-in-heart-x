package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

interface PlaylistRepository {
    /**
     * 获取当前歌单列表。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅返回内存态快照。
     * 内存变更后的持久化由实现侧异步完成，**不可假设返回时已落盘**。
     */
    fun getPlaylists(): List<Playlist>

    /**
     * 创建歌单。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅更新内存态。
     * 持久化异步完成，**不可假设返回时已落盘**。
     */
    fun createPlaylist(name: String): Playlist?

    /**
     * 删除歌单。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅更新内存态。
     * 持久化异步完成，**不可假设返回时已落盘**。
     */
    fun deletePlaylist(playlistId: Int): Boolean

    /**
     * 重命名歌单。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅更新内存态。
     * 持久化异步完成，**不可假设返回时已落盘**。
     */
    fun renamePlaylist(playlistId: Int, newName: String): Boolean

    /**
     * 向歌单添加歌曲。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅更新内存态。
     * 持久化异步完成，**不可假设返回时已落盘**。
     */
    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean

    /**
     * 将歌曲从歌单移除。
     *
     * m-1（评审）：本方法为非 suspend 同步方法，仅更新内存态。
     * 持久化异步完成，**不可假设返回时已落盘**。
     */
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
