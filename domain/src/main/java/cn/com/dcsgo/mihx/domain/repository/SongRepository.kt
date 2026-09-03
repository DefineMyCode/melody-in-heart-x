package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

interface SongRepository {
    suspend fun loadSongs(): List<Song>

    /** 曲库歌曲数(轻量, 不触发全库恢复) */
    suspend fun countSongs(): Int
    fun observeSongsSnapshot(): List<Song>
    fun setSongsChangedListener(listener: (() -> Unit)?)

    /** 查询持久化的歌手目录 */
    suspend fun loadLibraryArtists(): List<ArtistEntry>

    /** 查询持久化的专辑目录 */
    suspend fun loadLibraryAlbums(): List<AlbumEntry>

    fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean

    /**
     * 删除歌曲（M-3，评审 2026-09-03）：SAF 物理文件删除是 ContentProvider 跨进程调用，
     * 必须以 suspend 暴露并由实现侧调度到 IO，禁止在主线程同步执行。
     */
    suspend fun deleteSong(songId: Int): DeleteSongResult

    /**
     * 校验本地歌曲文件有效性：扫描每首歌曲对应的文件是否存在，
     * 清理文件已缺失的歌曲及其关联数据，返回处理汇总。
     */
    suspend fun validateAndCleanupLocalFiles(): LocalFileValidationResult
}
