package cn.com.dcsgo.mihx.core.model

/**
 * 情绪词表: 10 组 × 4 中文词, 每组锚定一个 V-A 坐标.
 *
 * 分层双轨——底层 V-A 连续坐标可排序可插值, 表层中文词可运营可读.
 * 词表只做"映射展示层", 绝不做分类训练目标.
 * 组代表词(words[0])用于自动分析展示; 用户手动标记可选组内任意词.
 *
 * [auto]=false 的组退出自动投票, 仅手动标记可用:
 * 鬼畜/沙雕是文化梗(故意做坏/重复剪切), 声学上无法与正经燃/快乐区分;
 * 且其锚点位于 V-A 图中心, YAMNet 偏暖的"中庸"窗会被整片吸走造成误标.
 *
 * ## 锚点表为什么变过(2026-10-25)
 *
 * 锚点属于**坐标系**, 坐标系定义一变, 锚点就必须跟着重标定.
 * 2026-10-25 落地了 [EmotionAnalyzer] 的两项改造:
 *   A  arousal 改成真实响度包络(RMS→dB→固定映射), 不再是 VA 头的输出
 *   B  valence 做了零点/斜率标定 v'=(v-0.25)/0.85
 * 这两项把 V-A 平面的定义改了, 旧锚点整体脱节——最典型的是「神圣」:
 * 它的旧锚点 (+0.20,+0.50) 恰好落在新云最密的位置, 于是退化成万能桶,
 * 独占 23.9% 的窗口, 而它本该只占 ~15%.
 *
 * 新锚点由 [tools/anchor_opt.py] 在**全库 1095 首真机真值**上受约束优化得到
 * (语义极值硬约束: 燃=最高能量/静谧=最低能量/窒息=最负面/元气与治愈并列最高正面;
 * 占比约束: 每组 3%~22%、燃≥8%、神圣≤18%; 外加最小间距 0.25、边界 0.95),
 * 并用 [tools/anchor_fit.py] 的真指标复核. 详见
 * docs/architecture/EMOTION_VA_UPGRADE.md §10.
 *
 * 锚点**只影响投票**, 不影响已存曲线 —— 所以重标定不需要重扫全库.
 */
enum class EmotionGroup(
    val words: List<String>,
    /** 锚定 valence (-1..1) */
    val anchorV: Float,
    /** 锚定 arousal (-1..1) */
    val anchorA: Float,
    /** 是否参与自动曲线投票(手动标记不受限) */
    val auto: Boolean = true,
) {
    BRIGHT(listOf("元气", "快乐", "轻快", "俏皮"), 0.486f, 0.492f),
    RAGE(listOf("燃", "热血", "战斗", "力量"), 0.009f, 0.607f),
    EPIC(listOf("神圣", "浩瀚", "震撼", "史诗"), 0.228f, 0.277f),
    WITTY(listOf("鬼畜", "沙雕", "戏谑", "荒诞"), 0.2f, 0.3f, auto = false),
    LOVE(listOf("治愈", "心动", "缱绻", "深情"), 0.431f, -0.124f),
    STILL(listOf("静谧", "空灵", "禅", "专注"), 0.078f, -0.733f),
    SAD(listOf("emo", "伤感", "遗憾", "雨季"), -0.744f, -0.432f),
    ANXIOUS(listOf("躁", "焦虑", "压迫", "狂躁"), -0.769f, 0.590f),
    CHILL(listOf("放松", "慵懒", "午后", "微醺"), 0.012f, -0.310f),
    ABYSS(listOf("窒息", "孤独", "深渊"), -0.774f, -0.104f),
    ;

    /** 组代表词(自动分析展示用) */
    val headline: String get() = words.first()

    companion object {
        /** 所有词 → 组 的反查表(懒加载, 词不重复). */
        private val wordIndex: Map<String, EmotionGroup> by lazy {
            entries.flatMap { g -> g.words.map { it to g } }.toMap()
        }

        fun groupOf(word: String): EmotionGroup? = wordIndex[word]

        /** 最多标记/自动展示的词数 */
        const val MAX_TAGS = 4

        /**
         * 逐窗曲线 → 组投票: 每窗找最近锚点组, 按占比排序取 top-n.
         * 近零窗弃权. 返回组+占比, 展示用 headline.
         */
        fun categoriesFor(
            curve: List<Pair<Float, Float>>,
            topN: Int = MAX_TAGS,
            minRatio: Float = 0.15f,
        ): List<Pair<EmotionGroup, Float>> {
            if (curve.isEmpty()) return emptyList()
            val votes = HashMap<EmotionGroup, Int>()
            var total = 0
            for ((v, a) in curve) {
                if (kotlin.math.abs(v) < SongEmotion.LOW_CONF &&
                    kotlin.math.abs(a) < SongEmotion.LOW_CONF
                ) continue
                total++
                val nearest = entries.filter { it.auto }.minByOrNull { g ->
                    val dv = v - g.anchorV
                    val da = a - g.anchorA
                    dv * dv + da * da
                } ?: continue
                votes[nearest] = (votes[nearest] ?: 0) + 1
            }
            if (total == 0) return emptyList()
            return votes.entries
                .map { (g, n) -> g to n.toFloat() / total }
                .filter { it.second >= minRatio }
                .sortedByDescending { it.second }
                .take(topN)
        }

        /** 自动分析展示词(组代表词列表). */
        fun headlineTagsFor(curve: List<Pair<Float, Float>>): List<String> =
            categoriesFor(curve).map { it.first.headline }

        /** 用户勾选词 → 平均坐标(组锚点均值). */
        fun avgOfWords(words: List<String>): Pair<Float, Float>? {
            val groups = words.mapNotNull { groupOf(it) }.distinct()
            if (groups.isEmpty()) return null
            return groups.map { it.anchorV }.average().toFloat() to
                groups.map { it.anchorA }.average().toFloat()
        }
    }
}
