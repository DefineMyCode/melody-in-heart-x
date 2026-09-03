package cn.com.dcsgo.mihx.core.model

/**
 * 随心播放增强的时段配置（一期：时段 × 情绪词条，无场景抽象）。
 *
 * 时间段为当日分钟数（0–1439），左闭右开 `[startMinutes, endMinutes)`；
 * `endMinutes <= startMinutes` 表示跨午夜（如 22:00–06:00）；
 * `startMinutes == endMinutes` 为零长度，属非法配置（校验拒绝保存）。
 *
 * @property id          唯一标识（毫秒时间戳生成即可，配置量级为个位数～十几条）
 * @property name        时段名，如「深夜静谧」「通勤提神」
 * @property startMinutes 开始分钟（当日 0–1439，含）
 * @property endMinutes   结束分钟（当日 0–1439，不含；<= start 表示跨午夜）
 * @property tags        情绪词条（EmotionGroup 词表内的词，多选；至少一个才可保存）
 */
data class TimeSlotConfig(
    val id: Long,
    val name: String,
    val startMinutes: Int,
    val endMinutes: Int,
    val tags: List<String>,
) {
    /** 是否跨午夜（end <= start 且非零长度） */
    val crossesMidnight: Boolean get() = endMinutes <= startMinutes

    /** 换算为展示用的时间段文本（24h 制），如 "22:00 – 06:00" */
    val timeRangeText: String
        get() = "${formatMinutes(startMinutes)} – ${formatMinutes(endMinutes)}"

    /** 是否覆盖指定的当日分钟 */
    fun covers(minuteOfDay: Int): Boolean {
        val m = ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (crossesMidnight) {
            m >= startMinutes || m < endMinutes
        } else {
            m >= startMinutes && m < endMinutes
        }
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60

        fun formatMinutes(minutes: Int): String {
            val m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            return "%02d:%02d".format(m / 60, m % 60)
        }
    }
}
