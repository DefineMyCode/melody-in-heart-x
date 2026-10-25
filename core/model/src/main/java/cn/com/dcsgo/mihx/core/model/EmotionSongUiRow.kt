package cn.com.dcsgo.mihx.core.model

/** 情绪歌曲列表行(拍平): 曲库「情绪」Tab 与情绪分析详情页共用. */
data class EmotionSongUiRow(
    val song: Song,
    /** 展示词条(用户校准优先, 否则曲线投票) */
    val tags: List<String>,
    /** 用户手动标记过 */
    val corrected: Boolean,
    /**
     * 把握程度 —— 列表行用它决定要不要给词条加提示。
     *
     * 列表行的 subline 只有一行, 放不下详情页那两句完整文案,
     * 所以这里只带**档位**, 具体怎么显示由 UI 层决定。
     */
    val confidence: EmotionConfidenceLevel = EmotionConfidenceLevel.HIGH,
)
