package cn.com.dcsgo.mihx.data.database.entity

/**
 * Read-only projection joining play stats with skip / short-play counters (plan P5-C).
 *
 * Not an `@Entity` — Room maps it from the LEFT JOIN in
 * [cn.com.dcsgo.mihx.data.database.dao.MelodyDao.getPlayStatsList].
 */
data class PlayStatsView(
    val songId: Long,
    val playCount: Long,
    val totalPlayedMs: Long,
    val lastPlayedAt: Long,
    val skipCount: Int,
    val shortPlayCount: Int,
)
