package cn.com.dcsgo.mihx.app.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全局播放抽屉（P3）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §六 P3
 *
 * 用途：当骨架描述声明 `playerEntry = "sheet"` 时，播放页不再是底栏 Tab，
 * 而是由迷你播放条拉起的全局抽屉。容器**复用仓库既有写法**
 * （`feature/player/.../PlayQueueSheet.kt:120` 的 `ModalBottomSheet`），不新造控件。
 *
 * ## ⚠️ 已核实的坑（两条来自方案D 的教训，一条是本次真机验收抓出来的）
 *
 * **1. 回调漏接线会让页面"假死"。** 若 `onDismissRequest` 拿到空 lambda，
 * 模态窗口会残留并继续拦截触摸——表现为"页面无响应但播放继续"。
 * 因此本组件的 `onDismiss` 是**必填参数**（无默认值），从类型层面杜绝漏接线。
 *
 * **2. BACK 键不走 onDismissRequest 的常规路径。** M3 的 `ModalBottomSheet` 渲染在
 * **独立 dialog window**，其返回处理由 sheet 自身消费后再回调 `onDismissRequest`。
 * 只要接线正确即可工作（真机已验证）。**不要**在主 window 另挂 `BackHandler` 抢它。
 *
 * **3. ★ 不要覆写 `confirmValueChange` 去拦截 `Hidden`（真机验收抓到的真 bug）。**
 * 本组件初版写成 `confirmValueChange = { it != SheetValue.Hidden }`，本意是"避免僵尸态"，
 * 实际后果是**用户下滑手势关闭被彻底禁用**——
 *   - 系统返回键：走 `onDismissRequest`，仍能关闭 ← 所以单测/粗测发现不了；
 *   - 下滑关闭：需要 sheet 状态转到 `Hidden`，被拦下 ← **静默失效**。
 * 真机实测症状：抽屉只能靠返回键关，"从顶部把手下滑"完全无反应。
 * **正解 = 不覆写 `confirmValueChange`**，用默认值（允许 `Hidden`），
 * 关闭意图一律由 `onDismissRequest` 通知上层；上层把 `isShown` 置 false，
 * 由 `if (!isShown) return` 卸载整个 sheet——状态只有一个真相，不存在僵尸态。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheetHost(
    isShown: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // 唯一真相是调用方的 isShown：关闭后直接卸载，因此不需要额外同步 sheet 状态。
    if (!isShown) return

    // 使用默认 confirmValueChange（允许 Hidden），下滑/点遮罩/返回键都能正常关闭。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        content()
    }
}
