package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.domain.repository.DayDuration
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private const val TAG = "PlayStatsRepository"
private const val PREFS_NAME = "play_stats_prefs"
private const val KEY_PREFIX = "play_count_"
private const val KEY_RAW_PLAY_COUNT_PREFIX = "raw_play_count_"
private const val KEY_PLAY_DURATION_PREFIX = "play_duration_"

/**
 * 播放统计仓库
 *
 * 以 song.id 为键，独立存储每首歌曲的播放统计。
 * 与 songs JSON 分离，避免修改 Song 数据模型。
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
    context: Context? = null,
    private val melodyDao: MelodyDao? = null,
) : cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository {

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

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
    override fun getCounts(songIds: List<Int>): Map<Int, Int> =
        songIds.associateWith { getCount(it) }

    /**
     * 批量获取原始播放次数
     * @param songIds 歌曲 ID 列表
     * @return Map<songId, rawPlayCount>
     */
    override fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int> =
        songIds.associateWith { getRawPlayCount(it) }

    override fun recordCompletedPlay(songId: Int) {
        increment(songId)
    }

    override fun recordPlaybackSession(
        songId: Int,
        startedAtMs: Long,
        durationMs: Long,
        isEffectivePlay: Boolean,
    ) {
        val dao = melodyDao ?: return
        runBlocking(Dispatchers.IO) {
            dao.insertPlaybackEvent(
                PlaybackEventEntity(
                    songId = songId,
                    startedAtMs = startedAtMs,
                    durationMs = durationMs.coerceAtLeast(0L),
                    isEffectivePlay = isEffectivePlay,
                ),
            )
        }
    }

    override fun playbackStatsSnapshot(): PlaybackStatsSnapshot {
        val dao = melodyDao ?: return PlaybackStatsSnapshot.EMPTY
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monthStart = today.withDayOfMonth(1)
        val nowMs = System.currentTimeMillis()

        fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

        fun endOfDay(date: LocalDate): Long =
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        return runBlocking(Dispatchers.IO) {
            val todayDurationMs = dao.totalDurationBetween(startOfDay(today), endOfDay(today))
            val todaySongCount = dao.distinctSongsBetween(startOfDay(today), endOfDay(today))
            val yesterdayDurationMs =
                dao.totalDurationBetween(startOfDay(today.minusDays(1)), endOfDay(today.minusDays(1)))
            val lastWeekSameDayDurationMs = dao.totalDurationBetween(
                startOfDay(today.minusWeeks(1)),
                endOfDay(today.minusWeeks(1)),
            )
            val weekTotalMs = dao.totalDurationBetween(startOfDay(weekStart), nowMs)
            val lastWeekTotalMs = dao.totalDurationBetween(
                startOfDay(weekStart.minusWeeks(1)),
                startOfDay(weekStart),
            )
            val daily = dao.dailyDurationsBetween(startOfDay(weekStart), nowMs)
                .associate { it.day to it.totalMs }
            val weekDays = (0..6).map { offset ->
                val day = weekStart.plusDays(offset.toLong())
                DayDuration(day, daily[day.toString()] ?: 0L)
            }
            val weeklyTop = dao.playCountsBetween(startOfDay(weekStart), nowMs)
                .take(PlaybackStatsSnapshot.WEEKLY_TOP_SIZE)
                .map { it.songId to it.playCount }
            val monthlyTop = dao.playCountsBetween(startOfDay(monthStart), nowMs)
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
    fun getAllCounts(): Map<Int, Int> {
        val dao = melodyDao
        if (dao != null) {
            return runBlocking(Dispatchers.IO) {
                dao.playStats().associate { it.songId to it.playCount }
            }
        }
        return prefs?.all.orEmpty()
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapKeys { it.key.removePrefix(KEY_PREFIX).toIntOrNull() ?: -1 }
            .filterKeys { it >= 0 }
            .mapValues { (it.value as? Int) ?: 0 }
    }

    override fun getRankedCounts(
        useRawCounts: Boolean,
        descending: Boolean,
    ): List<Pair<Int, Int>> {
        val stats = melodyDao?.let { dao ->
            runBlocking(Dispatchers.IO) { dao.playStats() }
        } ?: legacyStats()

        val comparator = if (descending) {
            compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first }
        } else {
            compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first }
        }
        val ranked = stats
            .map { stat ->
                stat.songId to if (useRawCounts) stat.rawPlayCount else stat.playCount
            }
            .filter { (_, count) -> count > 0 }
            .sortedWith(comparator)

        return ranked
    }

    private fun readStat(songId: Int): PlayStatsEntity {
        val dao = melodyDao
        if (dao != null) {
            return runBlocking(Dispatchers.IO) {
                dao.playStat(songId)
            } ?: legacyStat(songId)
        }
        return legacyStat(songId)
    }

    private fun writeStat(stat: PlayStatsEntity) {
        val dao = melodyDao
        if (dao != null) {
            runBlocking(Dispatchers.IO) {
                dao.upsertPlayStat(stat)
            }
        } else {
            requireNotNull(prefs) {
                "PlayStatsRepository requires Context when no MelodyDao is provided."
            }.edit()
                .putInt("$KEY_PREFIX${stat.songId}", stat.playCount)
                .putInt("$KEY_RAW_PLAY_COUNT_PREFIX${stat.songId}", stat.rawPlayCount)
                .putLong("$KEY_PLAY_DURATION_PREFIX${stat.songId}", stat.totalDurationMs)
                .apply()
        }
    }

    private fun legacyStat(songId: Int): PlayStatsEntity = PlayStatsEntity(
        songId = songId,
        playCount = prefs?.getInt("$KEY_PREFIX$songId", 0) ?: 0,
        rawPlayCount = prefs?.getInt("$KEY_RAW_PLAY_COUNT_PREFIX$songId", 0) ?: 0,
        totalDurationMs = prefs?.getLong("$KEY_PLAY_DURATION_PREFIX$songId", 0L) ?: 0L,
        lastPlayedAt = null,
    )

    private fun legacyStats(): List<PlayStatsEntity> {
        val preferences = prefs ?: return emptyList()
        val songIds = preferences.all.keys
            .mapNotNull { key ->
                when {
                    key.startsWith(KEY_PREFIX) -> key.removePrefix(KEY_PREFIX).toIntOrNull()
                    key.startsWith(KEY_RAW_PLAY_COUNT_PREFIX) -> key.removePrefix(KEY_RAW_PLAY_COUNT_PREFIX).toIntOrNull()
                    key.startsWith(KEY_PLAY_DURATION_PREFIX) -> key.removePrefix(KEY_PLAY_DURATION_PREFIX).toIntOrNull()
                    else -> null
                }
            }
            .toSet()

        return songIds.map(::legacyStat)
    }
}
