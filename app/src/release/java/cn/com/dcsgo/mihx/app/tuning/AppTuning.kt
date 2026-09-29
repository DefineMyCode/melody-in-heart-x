package cn.com.dcsgo.mihx.app.tuning

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * 调试面板的呈现接口。
 *
 * release 构建里 controller 恒为 null，因此 [UiTuningPanel] 永远不会被调用；
 * 但**类型必须存在**，否则 AppRoot 里对 `controller.showPanel` / `controller.tuning` 的
 * 引用在 release 源集直接编译不过（搭建期实踩：release 只留了 provider，漏了类型声明）。
 */
class UiTuningController internal constructor(
    val tuning: UiTuning,
    val onTuningChange: (UiTuning) -> Unit,
    val showPanel: Boolean,
    val onShowPanelChange: (Boolean) -> Unit,
    val onExport: () -> Unit,
    val onReset: () -> Unit,
)

/** 需要读取调参状态的消费方：我的页「版本」行（长按打开面板 / 显示当前生效状态） */
data class UiTuningAccess(
    val enabled: Boolean,
    val onOpenPanel: () -> Unit,
    val onExport: () -> Unit,
    /** P3：骨架（皮肤）切换信息。release 恒为空/默认，不暴露任何切换入口。 */
    val currentSkinId: String? = null,
    val skinOptions: List<Pair<String, String>> = emptyList(),
    val onSkinChange: (String) -> Unit = {},
)

/**
 * 下发调参令牌的 Provider。
 *
 * release 恒不注入任何值（controller 必为 null），排版读取点因此一律落到
 * `LocalXxxTokens` 的默认实例——等同于改造前的硬编码常量，行为/性能均无变化。
 */
@Composable
fun AppTuningProvider(
    controller: UiTuningController?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(content = content)
}

@Composable
internal fun rememberDebugTuning(): Pair<UiTuningController?, UiTuningAccess> =
    null to UiTuningAccess(enabled = false, onOpenPanel = {}, onExport = {})
