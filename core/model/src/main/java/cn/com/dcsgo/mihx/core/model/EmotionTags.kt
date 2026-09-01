package cn.com.dcsgo.mihx.core.model

/**
 * 展示词条: 用户手动标记词最高优先, 否则逐窗曲线投票.
 *
 * 曾有 kNN 锚点传播(未标记歌向相似的手动标记歌借词), 20 首真值实测
 * 传染对率 ~30% ≈ 瞎蒙, 且 embedding 空间音色主导、hubness 结构性误伤
 * (论证与数据: docs/architecture/EMOTION_KNN_ANALYSIS.md), 已整体下线.
 * 整曲 embedding 仍随分析入库——那是未来监督重训 VA 头的原料, 不是垃圾.
 */
fun emotionTagsOf(emotion: SongEmotion): List<String> {
    if (emotion.userTags.isNotEmpty()) return emotion.userTags
    return EmotionGroup.headlineTagsFor(emotion.curve)
}
