package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable

/**
 * 歌曲情绪分析"进度页": 批扫进度/统计 + 暂停/继续.
 * 歌曲列表(搜索/过滤/多选/播放)已搬去曲库「情绪」Tab.
 */
@Stable
data class EmotionAnalysisState(
    val analyzedCount: Int = 0,
    val totalCount: Int = 0,
    val scanning: Boolean = false,
    val paused: Boolean = false,
    val currentSongTitle: String? = null,
    val lastSongMs: Long = 0L,
    val avgSongMs: Long = 0L,
    val correctedCount: Int = 0,
)

data class EmotionAnalysisActions(
    val onBack: () -> Unit,
    /** 暂停/继续切换 */
    val onTogglePause: () -> Unit,
)

@Composable
fun EmotionAnalysisRoute(
    state: EmotionAnalysisState,
    actions: EmotionAnalysisActions,
) {
    EmotionAnalysisScreen(state = state, actions = actions)
}
