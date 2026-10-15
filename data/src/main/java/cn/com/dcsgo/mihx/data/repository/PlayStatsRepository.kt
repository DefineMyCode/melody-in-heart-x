package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.domain.repository.DayDuration
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private const val TAG = "PlayStatsRepository"

/**
 * 播放统计仓库
 *
 * 以 song.id 为键，独立存储每首歌曲的播放统计，唯一数据源为 Room。
 * 旧版 SharedPreferences 数据由 SharedPreferencesLegacyJsonMigration 一次性只读迁移。
 *
 * 有效播放次数规则（由调用方判断后调用 [increment]）：
 * - 歌曲已播放超过总时长的 90%，或
 * - 歌曲已播放超过 5 分钟（300,000 ms）
 * 两个条件满足其一即记一次，每次切歌重新计算。
 *
 * 原始播放次数规则（由调用方在开始新播放会话时调用 [incrementRawPlayCount]）：
 * - 歌曲开始播放即记一次
 * - 暂停后恢复不重复计数
 *
 * M-5（评审 2026-10-15）：接口改 suspend，直接挂起查询，不再 runBlocking 桥接。
 */
class PlayStatsRepository(
    private val melodyDao: MelodyDao,
) : cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository {

    /** 获取指定歌曲的播放次数 */
    suspend fun getCount(songId: Int): Int =
        readStat(songId).playCount

    /** 将指定歌曲的播放次数 +1，返回新的次数 */
    override suspend fun increment(songId: Int): Int {
        val now = System.currentTimeMillis()
        // SQL 原子自增（upsert + playCount=playCount+1），消除 read→write 两个独立事务间的 lost update
        withContext(Dispatchers.IO) { melodyDao.incrementPlayCount(songId, now) }
        val newCount = getCount(songId)
        AppLog.debug(TAG, "increment: song=$songId count=$newCount")
        return newCount
    }

    /** 获取指定歌曲的原始播放次数 */
    suspend fun getRawPlayCount(songId: Int): Int =
        readStat(songId).rawPlayCount

    /** 将指定歌曲的原始播放次数 +1，返回新的次数 */
    override suspend fun incrementRawPlayCount(songId: Int): Int {
        val now = System.currentTimeMillis()
        // SQL 原子自增
        withContext(Dispatchers.IO) { melodyDao.incrementRawPlayCount(songId, now) }
        val newCount = getRawPlayCount(songId)
        AppLog.debug(TAG, "incrementRawPlayCount: song=$songId count=$newCount")
        return newCount
    }

    /** 获取指定歌曲的累计播放时长（毫秒） */
    suspend fun getPlayDuration(songId: Int): Long =
        readStat(songId).totalDurationMs

    /** 更新指定歌曲的累计播放时长，返回新的时长 */
    suspend fun updatePlayDuration(songId: Int, durationMs: Long): Long {
        val now = System.currentTimeMillis()
        // SQL 原子累加
        withContext(Dispatchers.IO) { melodyDao.addPlayDuration(songId, durationMs, now) }
        val newDuration = getPlayDuration(songId)
        AppLog.debug(TAG, "updatePlayDuration: song=$songId duration=${newDuration}ms")
        return newDuration
    }

    /** 重置指定歌曲的累计播放时长 */
    suspend fun resetPlayDuration(songId: Int) {
        val current = readStat(songId)
        writeStat(current.copy(totalDurationMs = 0L))
        AppLog.debug(TAG, "resetPlayDuration: song=$songId")
    }

    /**
     * 批量获取播放次数
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, playCount>
     */
    override suspend fun getCounts(songIds: List<Int>): Map<Int, Int> = withContext(Dispatchers.IO) {
        val fromDb = melodyDao.playStatsIn(songIds).associate { it.songId to it.playCount }
        songIds.associateWith { fromDb[it] ?: 0 }
    }

    /**
     * 批量获取原始播放次数
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, rawPlayCount>
     */
    override suspend fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int> = withContext(Dispatchers.IO) {
        val fromDb = melodyDao.playStatsIn(songIds).associate { it.songId to it.rawPlayCount }
        songIds.associateWith { fromDb[it] ?: 0 }
    }

    override suspend fun recordCompletedPlay(songId: Int) {
        increment(songId)
    }

    override suspend fun recordPlaybackSession(
        songId: Int,
        startedAtMs: Long,
        durationMs: Long,
        isEffectivePlay: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            melodyDao.insertPlaybackEvent(
                PlaybackEventEntity(
                    songId = songId,
                    startedAtMs = startedAtMs,
                    durationMs = durationMs.coerceAtLeast(0L),
                    isEffectivePlay = isEffectivePlay,
                ),
            )
        }
    }

    override suspend fun playbackStatsSnapshot(): PlaybackStatsSnapshot {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        val nowMs = System.currentTimeMillis()

        fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

        fun endOfDay(date: LocalDate): Long =
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return withContext(Dispatchers.IO) {
            val todayDurationMs = melodyDao.totalDurationBetween(startOfDay(today), endOfDay(today))
            val todaySongCount = melodyDao.distinctSongsBetween(startOfDay(today), endOfDay(today))
            val yesterdayDurationMs = melodyDao.totalDurationBetween(
                startOfDay(today.minusDays(1)),
                endOfDay(today.minusDays(1)),
            )
            val lastWeekSameDayDurationMs = melodyDao.totalDurationBetween(
                startOfDay(today.minusWeeks(1)),
                endOfDay(today.minusWeeks(1)),
            )
            val weekTotalMs = melodyDao.totalDurationBetween(startOfDay(weekStart), nowMs)
            val lastWeekTotalMs = melodyDao.totalDurationBetween(
                startOfDay(weekStart.minusWeeks(1)),
                startOfDay(weekStart),
            )
            val daily = melodyDao.dailyDurationsBetween(startOfDay(weekStart), nowMs)
                .associate { it.day to it.totalMs }
            val weekDays = (0..6).map { offset ->
                val day = weekStart.plusDays(offset.toLong())
                DayDuration(day, daily[day.toString()] ?: 0L)
            }
            // M-4（评审 2026-10-15）：先过滤孤儿行（songId 不在曲库，如旧版本删歌残留）再 take(N)，
            // 否则孤儿行挤占 Top 名额，真实歌曲被顶出榜单。
            val knownSongIds = melodyDao.songs().mapTo(mutableSetOf()) { it.id }
            val weeklyTop = melodyDao.playCountsBetween(startOfDay(weekStart), nowMs)
                .filter { it.songId in knownSongIds }
                .take(PlaybackStatsSnapshot.WEEKLY_TOP_SIZE)
                .map { it.songId to it.playCount }
            val monthlyTop = melodyDao.playCountsBetween(startOfDay(monthStart), nowMs)
                .filter { it.songId in knownSongIds }
                .take(PlaybackStatsSnapshot.MONTHLY_TOP_SIZE)
                .map { it.songId to it.playCount }

            PlaybackStatsSnapshot(
                todayDurationMs = todayDurationMs,
                todaySongCount = todaySongCount,
                yesterdayDurationMs = yesterdayDurationMs,
                lastWeekSameDayDurationMs = lastWeekSameDayDurationMs,
                weekTotalMs = weekTotalMs,
                lastWeekTotalMs = lastWeekTotalMs,
                weekDays = weekDays,
                weeklyTop = weeklyTop,
                monthlyTop = monthlyTop,
            )
        }
    }

    /** 获取所有已有播放记录的 Map<songId, count> */
    suspend fun getAllCounts(): Map<Int, Int> = withContext(Dispatchers.IO) {
        melodyDao.playStats().associate { it.songId to it.playCount }
    }

    /** 批量取有效播放次数（排序用） */
    override suspend fun getPlayCountsForSort(): Map<Int, Int> = withContext(Dispatchers.IO) {
        melodyDao.playStats().associate { it.songId to it.playCount }
    }

    /** 批量取最近播放时间戳（排序用，null 不返回） */
    override suspend fun getLastPlayedAtForSort(): Map<Int, Long> = withContext(Dispatchers.IO) {
        melodyDao.playStats().mapNotNull { stat ->
            stat.lastPlayedAt?.let { stat.songId to it }
        }.toMap()
    }

    override suspend fun getRankedCounts(
        useRawCounts: Boolean,
        descending: Boolean,
    ): List<Pair<Int, Int>> {
        val stats = withContext(Dispatchers.IO) { melodyDao.playStats() }

        val comparator = if (descending) {
            compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }
        } else {
            compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first }
        }
        return stats
            .map { stat ->
                stat.songId to if (useRawCounts) stat.rawPlayCount else stat.playCount
            }
            .filter { (_, count) -> count > 0 }
            .sortedWith(comparator)
    }

    private suspend fun readStat(songId: Int): PlayStatsEntity =
        withContext(Dispatchers.IO) { melodyDao.playStat(songId) } ?: emptyStat(songId)

    private suspend fun writeStat(stat: PlayStatsEntity) {
        withContext(Dispatchers.IO) { melodyDao.upsertPlayStat(stat) }
    }

    private fun emptyStat(songId: Int): PlayStatsEntity = PlayStatsEntity(
        songId = songId,
        playCount = 0,
        rawPlayCount = 0,
        totalDurationMs = 0L,
        lastPlayedAt = null,
    )
}
