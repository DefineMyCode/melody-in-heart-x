package cn.com.dcsgo.mihx.core.model

/**
 * 单行歌词数据
 *
 * @param timeMs 该行歌词开始的时间戳（毫秒）
 * @param text 歌词文本内容
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * 歌词数据
 *
 * @param lines 按时间排序的歌词行列表
 * @param title 歌曲标题（可能为空）
 * @param artist 艺术家名称（可能为空）
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val title: String = "",
    val artist: String = ""
) {
    companion object {
        val EMPTY = Lyrics(emptyList())

        /**
         * 高亮提前量：预滚动补偿（滚动动画 ~300ms 与高亮过渡期间行尚未居中），
         * 2026-09-03 歌词实时性优化（ticker 200ms + offset 解析 + 本常量）。
         */
        const val HIGHLIGHT_LEAD_MS = 300L
    }

    /**
     * 根据当前播放进度，找到当前应该高亮的那一行歌词索引
     *
     * @param currentTimeMs 当前播放时间（毫秒）
     * @param leadMs 预滚动补偿（毫秒）：提前判定为当前行，使行"唱到时刚好滚到中央"。
     *   补偿滚动动画 + 高亮过渡的固有延迟（约 300ms）；负值可整体延后。
     * @return 当前应该高亮的歌词行索引，如果没有找到则返回 -1
     */
    fun getCurrentLineIndex(currentTimeMs: Long, leadMs: Long = HIGHLIGHT_LEAD_MS): Int {
        if (lines.isEmpty()) return -1

        // 找到最后一个小于等于（当前时间 + 提前量）的歌词行
        val effectiveTimeMs = currentTimeMs + leadMs
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= effectiveTimeMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}
