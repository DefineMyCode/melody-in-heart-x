package cn.com.dcsgo.mihx.domain.repository

import java.time.LocalDate

/**
 * 某一天的总听歌时长。
 *
 * @param day        自然日
 * @param durationMs 当天累计播放时长（毫秒）
 */
data class DayDuration(
    val day: LocalDate,
    val durationMs: Long,
)

/**
 * 播放统计中心的一次快照。
 *
 * 覆盖「今日 / 本周 / 本月」三个维度，供统计总览页与榜单页一次性渲染。
 *
 * @param todayDurationMs            今日累计听歌时长
 * @param todaySongCount             今日听过的歌曲数（去重）
 * @param yesterdayDurationMs        昨日时长（用于「较昨日」增量）
 * @param lastWeekSameDayDurationMs  上周同日时长（用于「较上周同日」增量）
 * @param weekTotalMs                本周累计听歌时长
 * @param lastWeekTotalMs            上周累计听歌时长（用于「较上周」百分比）
 * @param weekDays                   本周周一~周日逐日时长（自然周，周一起始）
 * @param weeklyTop                  本周歌曲有效播放次数榜（songId to playCount，降序，最多前 10）
 * @param monthlyTop                 本月歌曲有效播放次数榜（songId to playCount，降序，最多前 20）
 */
data class PlaybackStatsSnapshot(
    val todayDurationMs: Long,
    val todaySongCount: Int,
    val yesterdayDurationMs: Long,
    val lastWeekSameDayDurationMs: Long,
    val weekTotalMs: Long,
    val lastWeekTotalMs: Long,
    val weekDays: List<DayDuration>,
    val weeklyTop: List<Pair<Int, Int>>,
    val monthlyTop: List<Pair<Int, Int>>,
) {
    companion object {
        /** 本周歌曲 TOP 榜条数上限 */
        const val WEEKLY_TOP_SIZE = 10

        /** 本月歌曲 TOP 榜条数上限 */
        const val MONTHLY_TOP_SIZE = 20

        val EMPTY = PlaybackStatsSnapshot(
            todayDurationMs = 0L,
            todaySongCount = 0,
            yesterdayDurationMs = 0L,
            lastWeekSameDayDurationMs = 0L,
            weekTotalMs = 0L,
            lastWeekTotalMs = 0L,
            weekDays = emptyList(),
            weeklyTop = emptyList(),
            monthlyTop = emptyList(),
        )
    }
}
