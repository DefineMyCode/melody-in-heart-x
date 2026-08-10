package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.domain.repository.DayDuration
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
 */
class PlayStatsRepository(
    private val melodyDao: MelodyDao,
) : cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository {

    /** 获取指定歌曲的播放次数 */
    fun getCount(songId: Int): Int =
        readStat(songId).playCount

    /** 将指定歌曲的播放次数 +1，返回新的次数 */
    override fun increment(songId: Int): Int {
        val current = readStat(songId)
        val newCount = current.playCount + 1
        writeStat(current.copy(playCount = newCount, lastPlayedAt = System.currentTimeMillis()))
        AppLog.debug(TAG, "increment: song=$songId count=$newCount")
        return newCount
    }

    /** 获取指定歌曲的原始播放次数 */
    fun getRawPlayCount(songId: Int): Int =
        readStat(songId).rawPlayCount

    /** 将指定歌曲的原始播放次数 +1，返回新的次数 */
    override fun incrementRawPlayCount(songId: Int): Int {
        val current = readStat(songId)
        val newCount = current.rawPlayCount + 1
        writeStat(current.copy(rawPlayCount = newCount, lastPlayedAt = System.currentTimeMillis()))
        AppLog.debug(TAG, "incrementRawPlayCount: song=$songId count=$newCount")
        return newCount
    }

    /** 获取指定歌曲的累计播放时长（毫秒） */
    fun getPlayDuration(songId: Int): Long =
        readStat(songId).totalDurationMs

    /** 更新指定歌曲的累计播放时长，返回新的时长 */
    fun updatePlayDuration(songId: Int, durationMs: Long): Long {
        val current = readStat(songId)
        val newDuration = current.totalDurationMs + durationMs
        writeStat(current.copy(totalDurationMs = newDuration, lastPlayedAt = System.currentTimeMillis()))
        AppLog.debug(TAG, "updatePlayDuration: song=$songId duration=${newDuration}ms")
        return newDuration
    }

    /** 重置指定歌曲的累计播放时长 */
    fun resetPlayDuration(songId: Int) {
        val current = readStat(songId)
        writeStat(current.copy(totalDurationMs = 0L))
        AppLog.debug(TAG, "resetPlayDuration: song=$songId")
    }

    /**
     * 批量获取播放次数
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, playCount>
     */
    override fun getCounts(songIds: List<Int>): Map<Int, Int> = runBlocking(Dispatchers.IO) {
        val fromDb = melodyDao.playStatsIn(songIds).associate { it.songId to it.playCount }
        songIds.associateWith { fromDb[it] ?: 0 }
    }

    /**
     * 批量获取原始播放次数
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, rawPlayCount>
     */
    override fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int> = runBlocking(Dispatchers.IO) {
        val fromDb = melodyDao.playStatsIn(songIds).associate { it.songId to it.rawPlayCount }
        songIds.associateWith { fromDb[it] ?: 0 }
    }

    override fun recordCompletedPlay(songId: Int) {
        increment(songId)
    }

    override fun recordPlaybackSession(
        songId: Int,
        startedAtMs: Long,
        durationMs: Long,
        isEffectivePlay: Boolean,
    ) {
        runBlocking(Dispatchers.IO) {
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
            val weeklyTop = melodyDao.playCountsBetween(startOfDay(weekStart), nowMs)
                .take(PlaybackStatsSnapshot.WEEKLY_TOP_SIZE)
                .map { it.songId to it.playCount }
            val monthlyTop = melodyDao.playCountsBetween(startOfDay(monthStart), nowMs)
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
    fun getAllCounts(): Map<Int, Int> = runBlocking(Dispatchers.IO) {
        melodyDao.playStats().associate { it.songId to it.playCount }
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

    private fun readStat(songId: Int): PlayStatsEntity =
        runBlocking(Dispatchers.IO) { melodyDao.playStat(songId) } ?: emptyStat(songId)

    private fun writeStat(stat: PlayStatsEntity) {
        runBlocking(Dispatchers.IO) { melodyDao.upsertPlayStat(stat) }
    }

    private fun emptyStat(songId: Int): PlayStatsEntity = PlayStatsEntity(
        songId = songId,
        playCount = 0,
        rawPlayCount = 0,
        totalDurationMs = 0L,
        lastPlayedAt = null,
    )
}
