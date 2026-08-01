package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import javax.inject.Inject

class PlayStatsRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : PlayStatsRepository {
    override suspend fun getPlayCount(songId: Long): Long =
        dao.getPlayStats(songId)?.playCount ?: 0L

    override suspend fun recordPlay(songId: Long, durationMs: Long) {
        val existing = dao.getPlayStats(songId)
        val nextCount = (existing?.playCount ?: 0L) + 1
        dao.upsertPlayStats(
            PlayStatsEntity(
                songId = songId,
                playCount = nextCount,
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }
}
