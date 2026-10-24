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
         * 当前情绪模型标识。
         *
         * 放在 :core:model 是因为它零依赖, 不能反向依赖 :player;
         * :player 的 [cn.com.dcsgo.mihx.player.EmotionAnalyzer.MODEL_VERSION]
         * 直接引用本常量, 避免两处漂移。
         */
        const val CURRENT_MODEL_VERSION = "yamnet-va-v4-rms"

        /**
         * 改造前(v3 及更早)的锚点表 —— 那些曲线在**旧坐标系**里。
         *
         * A(arousal→RMS 响度) + B(valence 标定) 换了坐标系定义, 旧曲线必须
         * 配旧锚点投票。配错的后果是实测出来的: 旧曲线的 valence 系统性正偏
         * +0.264, 用新锚点投票会被当成"正面" —— 全库 1095 首里 **47.1% 变成
         * 「治愈」**, 负面词条(emo/躁/窒息)几乎消失。
         *
         * 重扫是后台渐进式的(每 6 小时 40 首、需充电), 混合坐标系会存在数天,
         * 所以必须按曲线自己的坐标系选锚点, 不能一刀切。
         */
        private val LEGACY_ANCHORS: Map<EmotionGroup, Pair<Float, Float>> = mapOf(
            BRIGHT to (0.6f to 0.4f),
            RAGE to (0.3f to 0.85f),
            EPIC to (0.2f to 0.5f),
            WITTY to (0.2f to 0.3f),
            LOVE to (0.6f to -0.1f),
            STILL to (0.3f to -0.55f),
            SAD to (-0.5f to -0.4f),
            ANXIOUS to (-0.4f to 0.6f),
            CHILL to (0.4f to -0.3f),
            ABYSS to (-0.7f to -0.2f),
        )

        /** 当前坐标系(v4)的锚点表 = 枚举自带的 anchorV/anchorA. */
        private val CURRENT_ANCHORS: Map<EmotionGroup, Pair<Float, Float>> by lazy {
            entries.associateWith { it.anchorV to it.anchorA }
        }

        /**
         * 按曲线所属坐标系取锚点表。
         *
         * 只有 [CURRENT_MODEL_VERSION] 产出的曲线在当前坐标系里; 更早的
         * modelVersion 一律用旧锚点.
         */
        fun anchorsFor(modelVersion: String): Map<EmotionGroup, Pair<Float, Float>> =
            if (modelVersion == CURRENT_MODEL_VERSION) CURRENT_ANCHORS else LEGACY_ANCHORS

        /**
         * 用户手动点该用哪套锚点表。
         *
         * - 有曲线: 必须与曲线**同坐标系**, 否则手动点在 V-A 图上与曲线错位
         * - 无曲线(未分析 / "user-only" 空曲线行): 未来的曲线一定由当前模型产出,
         *   用当前锚点, 将来重扫时继承下来才是对的
         */
        fun anchorsForManualPoint(
            emotion: SongEmotion?,
        ): Map<EmotionGroup, Pair<Float, Float>> =
            if (emotion != null && emotion.windowsAnalyzed > 0) {
                anchorsFor(emotion.modelVersion)
            } else {
                CURRENT_ANCHORS
            }

        /**
         * 逐窗曲线 → 组投票: 每窗找最近锚点组, 按占比排序取 top-n.
         * 近零窗弃权. 返回组+占比, 展示用 headline.
         *
         * [anchors] 必须与曲线所属坐标系匹配 —— 用 [anchorsFor] 按 modelVersion 取.
         */
        fun categoriesFor(
            curve: List<Pair<Float, Float>>,
            anchors: Map<EmotionGroup, Pair<Float, Float>>,
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
                    val p = anchors.getValue(g)
                    val dv = v - p.first
                    val da = a - p.second
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

        /**
         * 自动分析展示词(组代表词列表).
         *
         * [modelVersion] 决定用哪套锚点 —— 曲线和锚点必须同坐标系.
         */
        fun headlineTagsFor(
            curve: List<Pair<Float, Float>>,
            modelVersion: String,
        ): List<String> =
            categoriesFor(curve, anchorsFor(modelVersion)).map { it.first.headline }

        /**
         * 用户勾选词 → 平均坐标(组锚点均值).
         *
         * [anchors] 要与该歌曲线的坐标系一致, 这样用户点与曲线画在同一张 V-A 图上.
         */
        fun avgOfWords(
            words: List<String>,
            anchors: Map<EmotionGroup, Pair<Float, Float>> = CURRENT_ANCHORS,
        ): Pair<Float, Float>? {
            val groups = words.mapNotNull { groupOf(it) }.distinct()
            if (groups.isEmpty()) return null
            return groups.map { anchors.getValue(it).first }.average().toFloat() to
                groups.map { anchors.getValue(it).second }.average().toFloat()
        }
    }
}
