package cn.com.dcsgo.mihx.domain.repository

/**
 * 秒切歌曲仓库接口（域层）。
 * M-5（评审 2026-10-15）：全部改为 suspend —— 实现是 Room runBlocking 桥，
 * 同步签名曾迫使 AppNavHost 组合期、UI 点击线程直查数据库。
 */
interface QuickSkipRepository {
    suspend fun getSongIds(): Set<Int>
    suspend fun add(songId: Int): Boolean
    suspend fun remove(songId: Int): Boolean
    suspend fun contains(songId: Int): Boolean
    suspend fun incrementShortPlayCount(songId: Int): Int
    suspend fun resetShortPlayCount(songId: Int)
}
