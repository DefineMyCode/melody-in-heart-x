package cn.com.dcsgo.mihx.domain.repository

interface QuickSkipRepository {
    fun getSongIds(): Set<Int>
    fun add(songId: Int): Boolean
    fun remove(songId: Int): Boolean
    fun contains(songId: Int): Boolean
    fun incrementShortPlayCount(songId: Int): Int
    fun resetShortPlayCount(songId: Int)
}
