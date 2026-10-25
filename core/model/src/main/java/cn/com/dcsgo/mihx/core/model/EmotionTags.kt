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
 * 一个情绪词条 + 它在**整首**里占的比例（0~1）。
 *
 * @param ratio 该词条占全曲有效窗口的比例；**null = 用户手动标记** ——
 *   用户选的词是"我觉得这歌是它"，没有"占比"这回事，不该编一个数出来。
 */
data class EmotionTagRatio(
    val tag: String,
    val ratio: Float?,
)

/**
 * 时段匹配用的词条占比表（词 -> 在整曲里占的比例）。
 *
 * 与 [emotionTagRatiosOf] 的区别：
 *  - 这里给**完整分布**（不受展示门槛 15% 与 topN=4 限制）——
 *    展示层只显示前 4 个词，但匹配不能被这个限制截断
 *    （否则目标词排第 5 就永远匹配不上）。
 *  - 用户手动标记的词占比未知，按 **1.0**（强匹配）处理 ——
 *    那是用户自己的判断，比模型的更可信。
 */
fun emotionMatchRatiosOf(emotion: SongEmotion): Map<String, Float> {
    if (emotion.userTags.isNotEmpty()) {
        return emotion.userTags.associateWith { 1f }
    }
    return EmotionGroup.voteRatios(
        curve = emotion.compensatedCurve,
        anchors = EmotionGroup.anchorsFor(emotion.modelVersion),
    ).associate { (group, ratio) -> group.headline to ratio }
}

/**
 * 探索播放**计票**用的词条分布 —— 卡占比门槛，与详情页显示**同一套口径**。
 *
 * 为什么不能直接用 [emotionMatchRatiosOf] 的完整分布：
 * 完整分布是给**匹配**用的（时段匹配的目标词可能只占 10%，过滤掉就永远匹配不上）；
 * 但**计票**的语义是「你不喜欢这首歌体现出来的那些主导情绪」——
 * 一首 97% 的时间都不是「治愈」的歌，因为 3% 落在「治愈」附近就被记一笔
 * 「不喜欢治愈」，那是把噪音当信号。
 *
 * 实测全库 1095 首：完整分布平均每首歌 **6.26 个词**，
 * 卡 15% 后降到 **2.31 个**（正好等于详情页显示的词条数），
 * 而且**没有一首歌投不出票** —— 每首歌至少有一个组 ≥15%。
 *
 * 少投的那几个词正是「不喜欢」扩散的源头：
 * 每跳过一首歌同时否定 6.26 个词，比只否定主导词快 2.7 倍，
 * 于是「不喜欢」会在几十首歌内把所有词条染黑（实测 9 个 auto 组代表词全变 DISLIKE）。
 *
 * @param minRatio 占比门槛（默认与展示层一致）
 * @param topN 最多几个（默认与展示层一致）
 */
fun emotionVoteRatiosOf(
    emotion: SongEmotion,
    minRatio: Float = EmotionGroup.MIN_RATIO,
    topN: Int = EmotionGroup.MAX_TAGS,
): Map<String, Float> {
    if (emotion.userTags.isNotEmpty()) {
        // 用户手动标记: 那是用户自己的判断, 比模型的更可信 —— 按 1.0 强匹配处理
        return emotion.userTags.associateWith { 1f }
    }
    return EmotionGroup.categoriesFor(
        curve = emotion.compensatedCurve,
        anchors = EmotionGroup.anchorsFor(emotion.modelVersion),
        topN = topN,
        minRatio = minRatio,
    ).associate { (group, ratio) -> group.headline to ratio }
}

/**
 * 这首歌的情绪词条 + 各自占比。
 *
 * 占比 = 落在该组锚点附近的窗口占全曲**有效窗口**（近零窗弃权）的比例。
 * 换句话说：一首歌被判成「燃 35%、元气 22%」，
 * 意思是整首歌 35% 的时间落在「燃」附近、22% 落在「元气」附近。
 *
 * @param minRatio 只保留占比不低于此值的词条（展示层用；传 0f 拿**完整分布**）
 * @param topN 最多几个（展示层用）
 */
fun emotionTagRatiosOf(
    emotion: SongEmotion,
    minRatio: Float = EmotionGroup.MIN_RATIO,
    topN: Int = EmotionGroup.MAX_TAGS,
): List<EmotionTagRatio> {
    if (emotion.userTags.isNotEmpty()) {
        // 用户手动标记: 词条没有占比含义, 也不该跟模型词混在一个列表里比
        return emotion.userTags.map { EmotionTagRatio(it, null) }
    }
    return EmotionGroup.categoriesFor(
        curve = emotion.compensatedCurve,
        anchors = EmotionGroup.anchorsFor(emotion.modelVersion),
        topN = topN,
        minRatio = minRatio,
    ).map { EmotionTagRatio(it.first.headline, it.second) }
}

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
