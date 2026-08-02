package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.SongPlayStats
import kotlinx.coroutines.flow.Flow

/**
 * Playback statistics (plan P5-C): play counts / listening time feed the stats screen, and
 * [getPlayCounts] is the weight source for the uniform-random planner.
 */
interface PlayStatsRepository {
    suspend fun getPlayCount(songId: Long): Long

    /** Whole-library play counts keyed by song id — the uniform-random weight source. */
    suspend fun getPlayCounts(): Map<Long, Long>

    /** Records a settled play session: +1 play count and [durationMs] added to the total. */
    suspend fun recordPlay(songId: Long, durationMs: Long)

    /** Records that the user actively skipped away from the song. */
    suspend fun recordSkip(songId: Long)

    /** Records a "秒切": skipped away below the short-play threshold. */
    suspend fun recordShortPlay(songId: Long)

    suspend fun getStats(songId: Long): SongPlayStats?

    /** Total listening time across all songs, in milliseconds. */
    suspend fun getTotalPlayedMs(): Long

    fun observeStats(): Flow<List<SongPlayStats>>
}
