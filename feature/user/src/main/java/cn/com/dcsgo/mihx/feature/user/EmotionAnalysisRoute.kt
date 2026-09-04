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
    /** 分析失败歌曲（标题 + 失败记录），UI 展示"无法分析"分区（2026-09-04） */
    val failures: List<FailedEmotionSong> = emptyList(),
)

/** 失败歌曲行（songId + 标题 + 记录），详情页直接渲染 */
@Stable
data class FailedEmotionSong(
    val songId: Int,
    val title: String,
    val reason: cn.com.dcsgo.mihx.domain.repository.EmotionFailureReason,
    val attempts: Int,
)

data class EmotionAnalysisActions(
    val onBack: () -> Unit,
    /** 暂停/继续切换 */
    val onTogglePause: () -> Unit,
    /** 手动重试全部失败歌曲（清记录 + 重新入队） */
    val onRetryFailed: () -> Unit = {},
    /** 手动标记失败歌曲的情绪词条（分析失败歌无曲线，标记即用户结论） */
    val onCalibrateSong: (Int, Set<String>) -> Unit = { _, _ -> },
)

@Composable
fun EmotionAnalysisRoute(
    state: EmotionAnalysisState,
    actions: EmotionAnalysisActions,
) {
    EmotionAnalysisScreen(state = state, actions = actions)
}
