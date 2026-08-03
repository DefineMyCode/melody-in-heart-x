package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult

interface SongRepository {
    suspend fun loadSongs(): List<Song>
    fun observeSongsSnapshot(): List<Song>
    fun setSongsChangedListener(listener: (() -> Unit)?)

    /** 查询持久化的歌手目录 */
    suspend fun loadLibraryArtists(): List<ArtistEntry>

    /** 查询持久化的专辑目录 */
    suspend fun loadLibraryAlbums(): List<AlbumEntry>

    fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean
    fun deleteSong(songId: Int): DeleteSongResult
}
