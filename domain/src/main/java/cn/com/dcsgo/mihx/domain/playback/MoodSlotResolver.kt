package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.TimeSlotConfig

/**
 * 情境化随心播放的策略常量（一期）。
 *
 * 集中可调阈值，避免散落在 UI 与 planner 调用侧。
 */
object MoodSlotPolicy {
    /** 候选池低于该值时提示"歌曲较少，将循环播放"（正常生效） */
    const val POOL_WARN_THRESHOLD = 10

    /** 时段名最大长度（配置校验） */
    const val NAME_MAX_LENGTH = 20

    /** 每用户建议的最大时段数（软限制，超出时 UI 提示但不强制） */
    const val MAX_SLOTS = 10
}

/**
 * 情境化随心播放的时段判定与配置校验（域层纯函数，无 Android 依赖，可独立单测）。
 *
 * 生效规则（设计文档 §3.3）：
 * - 开关关闭 → 恒不生效；
 * - 区间左闭右开 `[start, end)`，`end <= start` 为跨午夜；
 * - 配置合法时（两两不重叠）同一时刻至多命中一个时段；命中多个（非法数据）取最早开始的那个，保证确定性；
 * - 词条集合按命中时段返回；无命中返回 null。
 */
class MoodSlotResolver {

    /** 当前时刻命中的时段词条；null = 增强未生效（开关关 / 无配置 / 无命中） */
    fun activeTags(
        configs: List<TimeSlotConfig>,
        enabled: Boolean,
        minuteOfDay: Int,
    ): Set<String>? {
        if (!enabled) return null
        val hit = hitSlot(configs, minuteOfDay) ?: return null
        return hit.tags.toSet()
    }

    /** 当前时刻命中的时段；多个命中（非法数据）时取开始时间最早者，保证确定性 */
    fun hitSlot(
        configs: List<TimeSlotConfig>,
        minuteOfDay: Int,
    ): TimeSlotConfig? = configs
        .filter { it.covers(minuteOfDay) }
        .minByOrNull { it.startMinutes }

    /** 配置是否可保存：名称非空不超长、词条非空、时间段非零长度、与已有配置不重叠 */
    fun validate(
        candidate: TimeSlotConfig,
        existing: List<TimeSlotConfig>,
    ): SlotValidation = when {
        candidate.name.isBlank() -> SlotValidation.Invalid(SlotError.EMPTY_NAME)
        candidate.name.length > MoodSlotPolicy.NAME_MAX_LENGTH ->
            SlotValidation.Invalid(SlotError.NAME_TOO_LONG)
        candidate.tags.isEmpty() -> SlotValidation.Invalid(SlotError.NO_TAGS)
        candidate.startMinutes == candidate.endMinutes ->
            SlotValidation.Invalid(SlotError.ZERO_LENGTH)
        else -> {
            val conflict = existing
                .filter { it.id != candidate.id }
                .firstOrNull { overlaps(candidate, it) }
            if (conflict != null) {
                SlotValidation.Conflict(conflict)
            } else {
                SlotValidation.Valid
            }
        }
    }

    /**
     * 环形时间轴两两判交（左闭右开）。
     *
     * 把每个区间拆成跨午夜前后的线性区间后按分钟数比较：
     * a 拆为 [aStart, 1440)∪[0, aEnd)（跨午夜时），同 b；非跨午夜即单段。
     * 任一子段对相交（含端点触碰按左闭右开判不含）即重叠。
     */
    fun overlaps(a: TimeSlotConfig, b: TimeSlotConfig): Boolean {
        val full = TimeSlotConfig.MINUTES_PER_DAY
        fun segments(start: Int, end: Int): List<Pair<Int, Int>> =
            if (start == end) {
                emptyList()
            } else if (end <= start) {
                listOf(start to full, 0 to end)
            } else {
                listOf(start to end)
            }

        val aSegs = segments(a.startMinutes, a.endMinutes)
        val bSegs = segments(b.startMinutes, b.endMinutes)
        for ((aStart, aEnd) in aSegs) {
            for ((bStart, bEnd) in bSegs) {
                // 左闭右开相交：aStart < bEnd && bStart < aEnd
                if (aStart < bEnd && bStart < aEnd) return true
            }
        }
        return false
    }
}

/** 校验结果：Valid 可保存；Invalid 参数错误；Conflict 与已有时段重叠 */
sealed class SlotValidation {
    data object Valid : SlotValidation()
    data class Invalid(val error: SlotError) : SlotValidation()
    data class Conflict(val conflicting: TimeSlotConfig) : SlotValidation()
}

enum class SlotError {
    EMPTY_NAME,
    NAME_TOO_LONG,
    NO_TAGS,
    ZERO_LENGTH,
}
