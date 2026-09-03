package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig

/** 情境化随心播放配置列表页公共状态 */
@Stable
data class MoodTimeSlotRouteState(
    val configs: List<TimeSlotConfig>,
    val enabled: Boolean,
    /** 词条 → 关联歌曲数（缺失词条视为 0） */
    val tagCounts: Map<String, Int>,
    /** 曲库歌曲总数（合计条分母） */
    val librarySize: Int,
    /** 当前时刻分钟数（0–1439，用于“生效中”徽标；由调用方定时刷新） */
    val nowMinuteOfDay: Int,
)

/** 情境化随心播放配置列表页公共操作 */
@Stable
data class MoodTimeSlotRouteActions(
    val onBack: () -> Unit,
    val onToggleEnabled: (Boolean) -> Unit,
    val onEditSlot: (TimeSlotConfig) -> Unit,
    val onDeleteSlot: (Long) -> Unit,
    val onAddSlot: () -> Unit,
)

@Composable
fun MoodTimeSlotRoute(
    state: MoodTimeSlotRouteState,
    actions: MoodTimeSlotRouteActions,
    showToast: (String) -> Unit,
) {
    MoodTimeSlotScreen(
        state = state,
        actions = actions,
        showToast = showToast,
    )
}
