package cn.com.dcsgo.mihx.domain.repository

interface PlayStatsRepository {
    suspend fun getPlayCount(songId: Long): Long
    suspend fun recordPlay(songId: Long, durationMs: Long)
}
