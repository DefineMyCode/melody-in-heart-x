package cn.com.dcsgo.mihx.core.model

/**
 * 一次投票派生的**全部**展示结论。
 *
 * 评审 2026-10-27 · M-11：原先情绪展示区为了拿到三样东西——
 * 展示词条、词条占比、把握档位——分别调 [emotionTagsOf] / [emotionTagRatiosOf] /
 * [emotionConfidenceLevelOf]，每个都**从头再投一遍票**，
 * 于是同一条曲线一次组合要投票 3~4 遍（每遍 ≈ 曲线窗数 × 候选组数 次距离计算，
 * 外加每次访问 `compensatedCurve` 重建整条补偿曲线）。
 *
 * 现在**只投票一次**，三样结论都从这一份投票派生 —— 结果与原先逐一致
 * （同一份投票、同一套过滤与阈值），只是不再重复算。
 */
data class EmotionDisplay(
    /** 展示用词条（用户手动标记优先） */
    val tags: List<String>,
    /** 展示用词条 + 各自占比（用户手动标记的词占比为 null） */
    val tagRatios: List<EmotionTagRatio>,
    /** 把握档位 */
    val confidence: EmotionConfidenceLevel,
)

/**
 * 这首歌的全部展示结论 —— **只投票一次**。
 *
 * @see EmotionDisplay
 */
fun emotionDisplayOf(emotion: SongEmotion): EmotionDisplay {
    // 用户已手动标记：展示的是用户自己选的词条，模型的把握程度与用户看到的无关，
    // 一律按 HIGH（与 [emotionConfidenceLevelOf] 的既有约定一致）。
    if (emotion.userTags.isNotEmpty()) {
        return EmotionDisplay(
            tags = emotion.userTags,
            tagRatios = emotion.userTags.map { EmotionTagRatio(it, null) },
            confidence = EmotionConfidenceLevel.HIGH,
        )
    }
    // 未分析 / 分析失败：没有模型输出可比，也没有词条。
    if (emotion.curve.isEmpty()) {
        return EmotionDisplay(
            tags = emptyList(),
            tagRatios = emptyList(),
            confidence = EmotionConfidenceLevel.HIGH,
        )
    }

    // ★ 唯一的一次投票 —— 下面三处结论都从它派生。
    //
    // 必须用**补偿后**的曲线（[SongEmotion.compensatedCurve]）：词条是按补偿后的
    // 曲线投票的，置信度必须描述**同一次投票**的把握程度；用补偿前的曲线算，
    // 等于在描述一个已经不存在的投票过程。
    val anchors = EmotionGroup.anchorsFor(emotion.modelVersion)
    val vote = EmotionGroup.voteRatios(emotion.compensatedCurve, anchors)

    val categories = EmotionGroup.categoriesFrom(vote)
    val tags = categories.map { it.first.headline }
    val tagRatios = categories.map { EmotionTagRatio(it.first.headline, it.second) }

    // 最高票占比 = 模型有没有把握（无有效窗时为 0f，即"完全没把握"）
    val confidenceValue = vote.firstOrNull()?.second ?: 0f
    val confidence = when {
        confidenceValue < LOW_CONFIDENCE_RATIO -> EmotionConfidenceLevel.SPREAD
        isMixedColdWarm(tags) -> EmotionConfidenceLevel.MIXED
        else -> EmotionConfidenceLevel.HIGH
    }

    return EmotionDisplay(tags = tags, tagRatios = tagRatios, confidence = confidence)
}
