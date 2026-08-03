package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult

interface SongRepository {
    suspend fun loadSongs(): List<Song>
    fun observeSongsSnapshot(): List<Song>
    fun setSongsChangedListener(listener: (() -> Unit)?)
    fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean
    fun deleteSong(songId: Int): DeleteSongResult
}
