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
    }

    /**
     * 根据当前播放进度，找到当前应该高亮的那一行歌词索引
     *
     * @param currentTimeMs 当前播放时间（毫秒）
     * @return 当前应该高亮的歌词行索引，如果没有找到则返回 -1
     */
    fun getCurrentLineIndex(currentTimeMs: Long): Int {
        if (lines.isEmpty()) return -1

        // 找到最后一个小于等于当前时间的歌词行
        var index = -1
        for (i in lines.indices) {
            if (lines[i].timeMs <= currentTimeMs) {
                index = i
            } else {
                break
            }
        }
        return index
    }
}
