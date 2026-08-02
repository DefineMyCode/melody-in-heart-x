package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.SongPlayStats
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsView
import cn.com.dcsgo.mihx.data.database.entity.ShortPlayCountEntity
import cn.com.dcsgo.mihx.data.database.entity.SkipSongEntity
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlayStatsRepositoryImpl @Inject constructor(
    private val dao: MelodyDao,
) : PlayStatsRepository {

    override suspend fun getPlayCount(songId: Long): Long =
        dao.getPlayStats(songId)?.playCount ?: 0L

    override suspend fun getPlayCounts(): Map<Long, Long> {
        val counts = HashMap<Long, Long>()
        dao.getPlayCounts().forEach { counts[it.songId] = it.playCount }
        return counts
    }

    override suspend fun recordPlay(songId: Long, durationMs: Long) {
        val existing = dao.getPlayStats(songId)
        dao.upsertPlayStats(
            PlayStatsEntity(
                songId = songId,
                playCount = (existing?.playCount ?: 0L) + 1L,
                totalPlayedMs = (existing?.totalPlayedMs ?: 0L) + durationMs.coerceAtLeast(0L),
                lastPlayedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun recordSkip(songId: Long) {
        val existing = dao.getSkipSong(songId)
        dao.upsertSkipSong(
            SkipSongEntity(songId = songId, skipCount = (existing?.skipCount ?: 0) + 1),
        )
    }

    override suspend fun recordShortPlay(songId: Long) {
        val existing = dao.getShortPlayCount(songId)
        dao.upsertShortPlayCount(
            ShortPlayCountEntity(
                songId = songId,
                shortPlayCount = (existing?.shortPlayCount ?: 0) + 1,
            ),
        )
    }

    override suspend fun getStats(songId: Long): SongPlayStats? {
        val stats = dao.getPlayStats(songId) ?: return null
        return SongPlayStats(
            songId = stats.songId,
            playCount = stats.playCount,
            totalPlayedMs = stats.totalPlayedMs,
            lastPlayedAt = stats.lastPlayedAt,
            skipCount = dao.getSkipSong(songId)?.skipCount ?: 0,
            shortPlayCount = dao.getShortPlayCount(songId)?.shortPlayCount ?: 0,
        )
    }

    override suspend fun getTotalPlayedMs(): Long = dao.getTotalPlayedMs()

    override fun observeStats(): Flow<List<SongPlayStats>> =
        dao.observePlayStatsList().map { rows -> rows.map(::toModel) }

    private fun toModel(view: PlayStatsView): SongPlayStats = SongPlayStats(
        songId = view.songId,
        playCount = view.playCount,
        totalPlayedMs = view.totalPlayedMs,
        lastPlayedAt = view.lastPlayedAt,
        skipCount = view.skipCount,
        shortPlayCount = view.shortPlayCount,
    )
}
