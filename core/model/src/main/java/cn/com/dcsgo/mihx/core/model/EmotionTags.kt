package cn.com.dcsgo.mihx.core.model

/**
 * 展示词条: 用户手动标记词最高优先, 否则逐窗曲线投票.
 *
 * 曾有 kNN 锚点传播(未标记歌向相似的手动标记歌借词), 20 首真值实测
 * 传染对率 ~30% ≈ 瞎蒙, 且 embedding 空间音色主导、hubness 结构性误伤
 * (论证与数据: docs/architecture/EMOTION_KNN_ANALYSIS.md), 已整体下线.
 * 整曲 embedding 仍随分析入库——那是未来监督重训 VA 头的原料, 不是垃圾.
 *
 * 投票必须用**曲线自己坐标系**的锚点表(见 [EmotionGroup.anchorsFor]):
 * 改造前的旧曲线还躺在库里(后台渐进重扫要数天), 用新锚点给旧曲线投票
 * 会把 valence 的系统性正偏当成"正面"——实测 47.1% 的歌会变成「治愈」。
 *
 * 投票还必须用**人声成分补偿后**的曲线(见 [SongEmotion.compensatedCurve]):
 * 补偿剥掉 valence 里"在测有没有人唱"的那部分, 否则器乐曲会被系统性判低
 * (实测全库器乐组 valence 平均低 0.162, 补偿后回到与独立模型一致的水平)。
 */
fun emotionTagsOf(emotion: SongEmotion): List<String> {
    if (emotion.userTags.isNotEmpty()) return emotion.userTags
    return EmotionGroup.headlineTagsFor(emotion.compensatedCurve, emotion.modelVersion)
}

/**
 * 模型**自动**词条（忽略用户校准的优先规则）。
 *
 * 与 [emotionTagsOf] 的区别：后者在用户已校准时返回用户词条，
 * 本函数始终从曲线重算模型的结论——判断"用户是否不认同模型"时必须用它，
 * 否则已校准的歌会拿用户自己的词当"模型词"比，比出来的永远是"没改"。
 *
 * 曲线为空（未分析/分析失败）→ 空列表，表示无模型输出可比。
 */
fun modelTagsOf(emotion: SongEmotion): List<String> =
    if (emotion.curve.isEmpty()) emptyList()
    else EmotionGroup.headlineTagsFor(emotion.compensatedCurve, emotion.modelVersion)

/**
 * 用户手动选的词条是否「不认同」模型结论。
 *
 * @param modelTags 模型自动词条（[modelTagsOf]），空列表 = 无模型输出可比
 * @param userWords 用户最终勾选的词条
 * @return true = 用户改了模型的词（独立纠正，可作真值）；
 *         false = 用户只是采纳了模型的词（点确认没改，无信息量）；
 *         null = 无模型输出可比（未分析/分析失败的歌）——不是"没改"，是"无从比较"。
 */
fun isUserCorrection(modelTags: List<String>, userWords: Collection<String>): Boolean? {
    if (modelTags.isEmpty()) return null
    return modelTags.toSet() != userWords.toSet()
}
