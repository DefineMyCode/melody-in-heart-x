package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.model.WordPreference

/** 情境化随心播放配置列表页公共状态 */
@Stable
data class MoodTimeSlotRouteState(
    val configs: List<TimeSlotConfig>,
    /** 随心播放增强运行模式（时段/探索互斥，2026-10-20） */
    val mode: RandomEnhanceMode,
    /** 词条 → 关联歌曲数（缺失词条视为 0） */
    val tagCounts: Map<String, Int>,
    /**
     * 按选中的词条算出**实际会进入随机池**的歌曲数。
     *
     * 不用「每词计数相加」—— 那样多词时同一首歌被重复计数，而且加上
     * 占比门槛后「带这个词」与「会匹配上」已经不是一回事。
     * 由调用方用与播放时同一套判定提供，保证界面数字 = 实际会播到的歌数。
     */
    val poolCountOf: (Set<String>) -> Int = { tags -> tags.sumOf { tagCounts[it] ?: 0 } },
    /** 曲库歌曲总数（合计条分母） */
    val librarySize: Int,
    /** 当前时刻分钟数（0–1439，用于“生效中”徽标；由调用方定时刷新） */
    val nowMinuteOfDay: Int,
    /** 探索播放：当前喜欢/中立/不喜欢词条（按词条名排序） */
    val wordPreferences: List<WordPreference> = emptyList(),
    /** 编辑/添加弹层是否打开：打开时隐藏列表页 FAB，避免悬浮在弹层上（2026-09-04 布局修复） */
    val dialogVisible: Boolean = false,
)

/** 情境化随心播放配置列表页公共操作 */
@Stable
data class MoodTimeSlotRouteActions(
    val onBack: () -> Unit,
    val onSelectMode: (RandomEnhanceMode) -> Unit,
    val onEditSlot: (TimeSlotConfig) -> Unit,
    val onDeleteSlot: (Long) -> Unit,
    val onAddSlot: () -> Unit,
    /** 探索播放：清空喜欢/不喜欢词条学习统计（重新探索） */
    val onClearWordPreferences: () -> Unit = {},
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
