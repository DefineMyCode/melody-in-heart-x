package cn.com.dcsgo.mihx.core.model

/** 情绪歌曲列表行(拍平): 曲库「情绪」Tab 与情绪分析详情页共用. */
data class EmotionSongUiRow(
    val song: Song,
    /** 展示词条(用户校准优先, 否则曲线投票) */
    val tags: List<String>,
    /** 用户手动标记过 */
    val corrected: Boolean,
)
