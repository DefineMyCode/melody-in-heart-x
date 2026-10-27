package cn.com.dcsgo.mihx.domain.repository

/**
 * 情绪表轻量统计。
 *
 * 评审 2026-10-27 · M-3：情绪扫描状态页原先每 2 秒轮询一次，轮询里读的是
 * **整表**（`analyzedVersions` 的 songId→modelVersion 映射 + `analyzedTimeline`
 * 的逐首时间戳）——1000 首库就是每 2 秒物化 2000 行。
 *
 * 改为读聚合：行数 + 最近一次分析时间。行数变化（新增/删除行）或最近分析时间
 * 变化（重扫）都能被检测到，足以作为「需要重建列表」的指纹；
 * 用户校准不走这条路径（校准由 `refresh()` 的手动触发器覆盖）。
 */
data class EmotionTableStat(
    /** song_emotions 总行数 */
    val count: Int,
    /** 最近一次分析完成时间（epoch ms）；无数据时为 0 */
    val maxAnalyzedAt: Long,
)
