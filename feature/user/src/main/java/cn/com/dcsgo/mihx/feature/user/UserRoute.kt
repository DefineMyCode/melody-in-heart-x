package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

@Stable
data class UserRouteState(
    /** 今日累计听歌时长（毫秒），用于「我的」页入口卡预览 */
    val todayDurationMs: Long = 0L,
    /** 本周累计听歌时长（毫秒），用于「我的」页入口卡预览 */
    val weekTotalMs: Long = 0L,
    /** 本地歌曲文件校验结果（未确认前保留） */
    val validationResult: LocalFileValidationResult? = null,
    /** 校验是否正在后台运行 */
    val isValidating: Boolean = false,
    /** 情绪分析: 已分析数/总数/是否扫描中 */
    val emotionAnalyzedCount: Int = 0,
    val emotionTotalCount: Int = 0,
    val emotionScanning: Boolean = false,
    /** 用户手动暂停批扫 */
    val emotionPaused: Boolean = false,
)

data class UserRouteActions(
    val onShowSettings: () -> Unit,
    val onShowPlaybackStats: () -> Unit,
    val onOpenFileCheck: () -> Unit,
    val onEmotionScanNow: () -> Unit = {},
    val onOpenEmotionAnalysis: () -> Unit = {},
)

@Composable
fun UserRoute(
    state: UserRouteState,
    actions: UserRouteActions,
) {
    UserScreen(
        onShowSettings = actions.onShowSettings,
        todayDurationMs = state.todayDurationMs,
        weekTotalMs = state.weekTotalMs,
        onOpenPlaybackStats = actions.onShowPlaybackStats,
        validationResult = state.validationResult,
        isValidating = state.isValidating,
        onOpenFileCheck = actions.onOpenFileCheck,
        emotionAnalyzedCount = state.emotionAnalyzedCount,
        emotionTotalCount = state.emotionTotalCount,
        emotionScanning = state.emotionScanning,
        emotionPaused = state.emotionPaused,
        onEmotionScanNow = actions.onEmotionScanNow,
        onOpenEmotionAnalysis = actions.onOpenEmotionAnalysis,
    )
}
