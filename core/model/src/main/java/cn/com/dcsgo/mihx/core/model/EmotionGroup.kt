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
 * 且其锚点(0.2,0.3)位于 V-A 图中心, YAMNet 偏暖的"中庸"窗会被整片吸走造成误标.
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
    BRIGHT(listOf("元气", "快乐", "轻快", "俏皮"), 0.6f, 0.4f),
    RAGE(listOf("燃", "热血", "战斗", "力量"), 0.3f, 0.85f),
    EPIC(listOf("神圣", "浩瀚", "震撼", "史诗"), 0.2f, 0.5f),
    WITTY(listOf("鬼畜", "沙雕", "戏谑", "荒诞"), 0.2f, 0.3f, auto = false),
    LOVE(listOf("治愈", "心动", "缱绻", "深情"), 0.6f, -0.1f),
    STILL(listOf("静谧", "空灵", "禅", "专注"), 0.3f, -0.55f),
    SAD(listOf("emo", "伤感", "遗憾", "雨季"), -0.5f, -0.4f),
    ANXIOUS(listOf("躁", "焦虑", "压迫", "狂躁"), -0.4f, 0.6f),
    CHILL(listOf("放松", "慵懒", "午后", "微醺"), 0.4f, -0.3f),
    ABYSS(listOf("窒息", "孤独", "深渊"), -0.7f, -0.2f),
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
