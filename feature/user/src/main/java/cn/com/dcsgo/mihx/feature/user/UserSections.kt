package cn.com.dcsgo.mihx.feature.user

/**
 * 「我的」页的分区标识（L2 分区化，2026-09-29 P4）。
 *
 * ## 为什么是字符串常量而不是 enum
 *
 * 这些标识要能被**皮肤描述**引用（描述是 JSON，只能是字符串），
 * 并且要能出现在任意子集、任意顺序里。enum 会诱导调用方穷举，
 * 而这里的语义恰恰是"可以有任意子集"。
 *
 * ## 谁在用它
 *
 * - `UserScreen` 按 [DEFAULT_ORDER] 的顺序渲染（= 改造前的写死顺序）；
 * - `:app` 把皮肤描述里 `myOverview` 分区的 `sections` 属性解析成一段 key 列表传进来，
 *   从而实现"删掉某张卡 / 调换顺序"。
 *
 * ## 与描述的一致性
 *
 * 本清单必须与 `DefaultSkin` 里 `myOverview` 的 `sections` 属性逐字一致，
 * 由 `UserSectionsMatchDescriptionTest` 对照回归（这是 P4 从"曲库分段写错"里学到的：
 * 描述与代码事实必须有测试绑定，不能靠自觉）。
 */
object UserSections {

    /** 用户信息（头像 + 应用名 + 版本行）。 */
    const val USER_INFO = "userInfo"

    /** 播放统计入口卡（今日/本周时长预览）。 */
    const val PLAY_STATS = "playStats"

    /** 情境化随心播放增强入口卡。 */
    const val MOOD_TIME_SLOT = "moodTimeSlot"

    /** 情绪分析进度卡。 */
    const val EMOTION_SCAN = "emotionScan"

    /** 本地文件校验入口卡。 */
    const val FILE_CHECK = "fileCheck"

    /** P5：用户自定义皮肤入口卡(Q3 = 另开一个入口)。 */
    const val CUSTOM_SKIN = "customSkin"

    /**
     * 改造前的写死顺序 —— 默认值，保证不传描述时行为与以前完全一致。
     *
     * 注意顺序与 `UserScreen` 里原 `LazyColumn` 的 item 顺序一一对应。
     */
    val DEFAULT_ORDER: List<String> = listOf(
        USER_INFO,
        PLAY_STATS,
        MOOD_TIME_SLOT,
        EMOTION_SCAN,
        FILE_CHECK,
        CUSTOM_SKIN,
    )

    /** 全部合法 key（供校验与测试使用）。 */
    val ALL: Set<String> = DEFAULT_ORDER.toSet()

    fun isKnown(key: String): Boolean = key in ALL

    /**
     * 把人写的逗号分隔串解析成 key 列表。
     *
     * 容错策略：忽略空白项与**未知 key**（未知项由 `:core:skin` 的校验器在导入期拦下，
     * 这里再兜一层是为了"坏描述不让页面崩"，符合启动路径 fail-safe 的既有原则）。
     */
    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return DEFAULT_ORDER
        val parsed = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() && isKnown(it) }
        return parsed.ifEmpty { DEFAULT_ORDER }
    }
}
