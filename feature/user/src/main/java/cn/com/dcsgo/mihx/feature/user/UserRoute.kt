package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
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
    /** 情境化随心播放：时段配置 + 开关（入口卡状态） */
    val moodSlotConfigs: List<TimeSlotConfig> = emptyList(),
    val moodSlotEnabled: Boolean = false,
    /** 当前时刻分钟数（0–1439），判定入口卡"生效中"态 */
    val nowMinuteOfDay: Int = 0,
    /**
     * 版本行长按回调（UI 调参面板入口）。
     * 由 :app 侧注入：debug 构建打开调试面板，release 恒为空实现——
     * 这样 feature 模块不需要感知 BuildConfig，也不会把调参逻辑编进正式包。
     */
    val onVersionLongPress: () -> Unit = {},
    /**
     * 我的页分区顺序（L2 分区化，2026-09-29 P4）。
     *
     * 默认 = 改造前的写死顺序，故**不传时行为零变化**；由 :app 从皮肤描述解析后注入。
     */
    val sectionOrder: List<String> = UserSections.DEFAULT_ORDER,
    // P5：用户自定义皮肤。name 为 null 时表示"未导入"。
    val hasUserSkin: Boolean = false,
    val userSkinName: String? = null,
)

data class UserRouteActions(
    val onShowSettings: () -> Unit,
    val onShowPlaybackStats: () -> Unit,
    val onOpenFileCheck: () -> Unit,
    val onEmotionScanNow: () -> Unit = {},
    val onOpenEmotionAnalysis: () -> Unit = {},
    /** 进入随心播放增强配置页 */
    val onOpenMoodTimeSlot: () -> Unit = {},
    val onOpenUserSkin: () -> Unit = {},
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
        moodSlotConfigs = state.moodSlotConfigs,
        moodSlotEnabled = state.moodSlotEnabled,
        nowMinuteOfDay = state.nowMinuteOfDay,
        onVersionLongPress = state.onVersionLongPress,
        onOpenMoodTimeSlot = actions.onOpenMoodTimeSlot,
        sectionOrder = state.sectionOrder,
        hasUserSkin = state.hasUserSkin,
        userSkinName = state.userSkinName,
        onOpenUserSkin = actions.onOpenUserSkin,
    )
}
