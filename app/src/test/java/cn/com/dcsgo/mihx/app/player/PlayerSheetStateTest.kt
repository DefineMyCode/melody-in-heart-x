package cn.com.dcsgo.mihx.app.player

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播放抽屉的 [SheetState] 构造契约（2026-10-24 真机崩溃回归）。
 *
 * ## 崩过的那个 bug
 *
 * 之前用的是 `rememberStandardBottomSheetState(initialValue = SheetValue.Hidden)`，
 * 真机点迷你条直接崩：
 *
 * ```
 * java.lang.IllegalArgumentException: The initial value must not be set to Hidden
 * if skipHiddenState is set to true.
 *   at androidx.compose.material3.SheetState.<init>(SheetDefaults.kt:107)
 * ```
 *
 * 根因：M3 1.4.0 的 `rememberStandardBottomSheetState` 的 `skipHiddenState` **默认 true**，
 * 而它的构造器里有 `require(initialValue != Hidden)` —— 两者同时成立就抛异常。
 * （顺带一提，它还会把 PartiallyExpanded 放进锚点，M3 的 `show()` 会停在半展开。）
 *
 * 正解是自己构造 [SheetState]：`skipPartiallyExpanded = true` + `skipHiddenState = false`，
 * 初值按需给 Hidden（正常滑起）/ Expanded（返回时瞬间出现）。
 *
 * 本测试把"这两种组合都不该抛异常"锁死——它是纯 JVM 可测的，
 * 不需要真机就能挡住这次回归。
 */
@OptIn(ExperimentalMaterial3Api::class)
class PlayerSheetStateTest {

    /** 抽屉只有开/关两态：不含 PartiallyExpanded（否则 show() 会停在半展开）。 */
    private fun newState(initialValue: SheetValue): SheetState = SheetState(
        skipPartiallyExpanded = true,
        positionalThreshold = { 56f },
        velocityThreshold = { 125f },
        initialValue = initialValue,
        confirmValueChange = { true },
        skipHiddenState = false,
    )

    @Test
    fun `hidden initial value does not throw`() {
        // 正常从迷你条拉起抽屉：初值 Hidden + skipHiddenState = false → 不能抛。
        val state = newState(SheetValue.Hidden)
        assertEquals(SheetValue.Hidden, state.currentValue)
    }

    @Test
    fun `expanded initial value does not throw`() {
        // 从详情页返回抽屉：初值 Expanded（挂载即展开，不重播滑起动画）。
        val state = newState(SheetValue.Expanded)
        assertEquals(SheetValue.Expanded, state.currentValue)
    }
}
