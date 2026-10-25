package cn.com.dcsgo.mihx.core.model

/**
 * 情绪词条的**把握程度**（置信度）。
 *
 * 信号 = 逐窗投票里最高票组的占比（见 [EmotionGroup.confidenceOf]）。
 * 这个量本来就在算, 但原先被 minRatio 过滤掉了 —— 等于白算。
 *
 * ## 为什么需要它
 *
 * 词条来自"每窗找最近锚点, 再按票数排序" —— 票摊得开,
 * 词条就会同时冒出冷词和暖词. 全库 1095 首里 **20.8%** 出现
 * 「治愈 · emo」这类自相矛盾的组合, 用户看到无从判断:
 * 这歌真这么矛盾? 还是模型自己没把握?
 *
 * 实测（真机全库 1095 首真值, 坐标系 yamnet-va-v5-vocal）：
 *  - 词条含冷暖矛盾的 228 首, top1 占比均值 0.398
 *  - 不矛盾的 867 首, top1 占比均值 0.484
 *  - Mann-Whitney U p = 1.7e-14 —— 差异极显著
 *
 * 也就是说 **top1 占比就是「模型有没有把握」的现成测量**:
 * 不用新算任何东西, 只要把它露出来.
 *
 * ## 阈值不是拍脑袋
 *
 * [LOW_CONFIDENCE_RATIO] = 0.35 取自全库分位（p25 = 0.345）.
 * 以 0.35 切, 低把握那批里 44.7% 词条自相矛盾, 高把握那批只有 23.6%
 * —— 分界线正好落在"矛盾开始扎堆"的位置, 两边矛盾率差近一倍.
 *
 * 换一个坐标系/换锚点表, 这个阈值要重新标（它依赖投票分布）;
 * 换模型资产则不影响（阈值只看票型, 不看绝对坐标）。
 */

/** 低把握阈值: 最高票占比低于此值 = 模型没把握。 */
const val LOW_CONFIDENCE_RATIO = 0.35f

/**
 * 把握程度的档位。
 *
 * 单一阈值（top1 < 0.35）不够：实测 **137 首（12.5%）** 词的 top1 占比很高
 * （中位 0.62，最高 0.80）却冷暖同现 —— 那不是"没把握"，是"很确定地
 * 给出了一对互相矛盾的结论"。两种情况对用户的含义不同, 文案也不能一样。
 */
enum class EmotionConfidenceLevel {
    /** 票集中且词条不打架 —— 可以放心看 */
    HIGH,

    /** 票摊开 —— 模型自己也没把握 */
    SPREAD,

    /** 票集中但冷暖词同现 —— 结论很确定, 但两极 */
    MIXED,
}

/** 冷组词（负面/躁动/压抑） */
private val COOL_WORDS = setOf("emo", "躁", "窒息")

/** 暖组词（正面/激昂/柔和）—— 「静谧」算中性, 不进这两组 */
private val WARM_WORDS = setOf("元气", "燃", "神圣", "治愈", "放松")

/**
 * 词条里是否冷暖同现（自相矛盾）。
 *
 * 「静谧」不算冷也不算暖 —— 它本身就是中性词, 与任何一组同现都不矛盾。
 */
fun isMixedColdWarm(tags: List<String>): Boolean {
    val s = tags.toSet()
    return s.any { it in COOL_WORDS } && s.any { it in WARM_WORDS }
}

/**
 * 这首歌的置信度 = 最高票组占比（0~1）。
 *
 * 越大 = 越多窗口落在同一个情绪组 = 模型越确定。
 *
 * **必须用补偿后的曲线**（[SongEmotion.compensatedCurve]）—— 词条是按补偿后
 * 的曲线投票的，置信度必须描述**同一次投票**的把握程度。
 * 用补偿前的曲线算，就等于在描述一个已经不存在的投票过程。
 *
 * null = 没有模型输出可比（未分析 / 分析失败, 曲线为空）。
 */
fun emotionConfidenceOf(emotion: SongEmotion): Float? =
    if (emotion.curve.isEmpty()) {
        null
    } else {
        // 必须用**补偿后**的曲线: 词条是按补偿后的曲线投票的,
        // 置信度必须描述**同一次投票**的把握程度。
        // 用补偿前的曲线算, 等于在描述一个已经不存在的投票过程。
        EmotionGroup.confidenceOf(
            emotion.compensatedCurve,
            EmotionGroup.anchorsFor(emotion.modelVersion),
        )
    }

/**
 * 这首歌的把握档位。
 *
 * **用户已手动标记的一律算 HIGH** —— 那时展示的是用户自己选的词条,
 * 模型的把握程度跟用户看到的词没关系, 提示只是噪音.
 *
 * 判定顺序：先看票型（摊不摊开），再看词条是否自相矛盾。
 * 两者都成立时以「摊开」为准 —— 因为那才是更根本的原因。
 */
fun emotionConfidenceLevelOf(emotion: SongEmotion): EmotionConfidenceLevel {
    if (emotion.userTags.isNotEmpty()) return EmotionConfidenceLevel.HIGH
    val confidence = emotionConfidenceOf(emotion) ?: return EmotionConfidenceLevel.HIGH
    if (confidence < LOW_CONFIDENCE_RATIO) return EmotionConfidenceLevel.SPREAD
    if (isMixedColdWarm(modelTagsOf(emotion))) return EmotionConfidenceLevel.MIXED
    return EmotionConfidenceLevel.HIGH
}

/**
 * 是否该向用户提示「把握不大」。
 *
 * [EmotionConfidenceLevel.SPREAD] 与 [EmotionConfidenceLevel.MIXED] 都要提示,
 * 但**文案不同** —— 见 UI 层。
 */
fun needsConfidenceHint(emotion: SongEmotion): Boolean =
    emotionConfidenceLevelOf(emotion) != EmotionConfidenceLevel.HIGH
