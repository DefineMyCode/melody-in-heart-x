package cn.com.dcsgo.mihx.core.model

/**
 * 情绪词条的**把握程度**（置信度）.
 *
 * 信号 = 逐窗投票里最高票组的占比（见 [EmotionGroup.confidenceOf]）.
 * 这个量本来就在算, 但原先被 minRatio 过滤掉了 —— 等于白算.
 *
 * ## 为什么需要它
 *
 * 词条来自"每窗找最近锚点, 再按票数排序" —— 票摊得开,
 * 词条就会同时冒出冷词和暖词. 全库 1095 首里 **29.0%** 出现
 * 「治愈 · emo」这类自相矛盾的组合, 用户看到无从判断:
 * 这歌真这么矛盾? 还是模型自己没把握?
 *
 * 实测（真机全库 1095 首真值, 坐标系 yamnet-va-v4-rms）:
 *  - 词条含冷暖矛盾的 318 首, top1 占比均值 0.392
 *  - 不矛盾的 774 首, top1 占比均值 0.487
 *  - Mann-Whitney U p = 8.8e-22 —— 差异极显著
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
 * 换模型资产则不影响（阈值只看票型, 不看绝对坐标）.
 */
const val LOW_CONFIDENCE_RATIO = 0.35f

/**
 * 这首歌的置信度 = 最高票组占比（0~1）.
 *
 * 越大 = 越多窗口落在同一个情绪组 = 模型越确定.
 *
 * null = 没有模型输出可比（未分析 / 分析失败, 曲线为空）.
 */
fun emotionConfidenceOf(emotion: SongEmotion): Float? =
    if (emotion.curve.isEmpty()) {
        null
    } else {
        EmotionGroup.confidenceOf(emotion.curve, EmotionGroup.anchorsFor(emotion.modelVersion))
    }

/**
 * 是否该向用户提示「模型把握不大」.
 *
 * **用户已手动标记的不算** —— 那时展示的是用户自己选的词条,
 * 模型的把握程度跟用户看到的词没关系, 提示只是噪音.
 */
fun needsConfidenceHint(emotion: SongEmotion): Boolean =
    emotion.userTags.isEmpty() &&
        (emotionConfidenceOf(emotion) ?: 1f) < LOW_CONFIDENCE_RATIO
