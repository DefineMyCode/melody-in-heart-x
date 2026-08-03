package cn.com.dcsgo.mihx.domain.quickskip

import cn.com.dcsgo.mihx.core.model.Song

interface QuickSkipActions {
    fun getSongs(): List<Song>
    fun add(songId: Int): Boolean
    fun remove(songId: Int): Boolean
    fun contains(songId: Int): Boolean
    fun syncToPlaylist()
}
