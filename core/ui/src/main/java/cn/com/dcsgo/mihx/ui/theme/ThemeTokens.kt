package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.runtime.Immutable
import cn.com.dcsgo.mihx.core.model.ThemeVariant

/**
 * 公开主题令牌：宿主 UI 的可变配色契约。
 *
 * 背景（2026-09-29 插件系统 spike）：T2 代码插件路线已跑通，但插件无法引用 `internal` 的
 * [ThemePalette]。这里把配色上提成公开、**用 ARGB Int 表示**的令牌类型：
 *  - Int ARGB 便于跨模块（尤其未来的插件契约）传递，无需依赖 compose-ui 的 Color 类型；
 *  - 内置 4 套预设（墨色/朱砂 × 明暗）转成 [ThemeTokens] 实例，默认值与原视觉**逐字段一致**。
 *
 * 注意：本类型只描述"值"，不含任何资源 id。插件系统实测（见 docs/architecture/
 * PLUGIN_SYSTEM_SPIKE_REPORT.md）已确认跨 dex 传递裸资源 id 有串号风险，因此契约一律传值。
 */
@Immutable
data class ThemeTokens(
    val bg0: Int,
    val bg1: Int,
    val bg2: Int,
    val bg3: Int,
    val bg4: Int,
    val out1: Int,
    val out2: Int,
    val text1: Int,
    val text2: Int,
    val text3: Int,
    val accent: Int,
    val onAccent: Int,
    val accent2: Int,
    val onAccent2: Int,
) {
    companion object {
        /** 墨色 · 浅色（与原 MonoLightColors 逐字段等值） */
        val MonoLight = ThemeTokens(
            bg0 = 0xFFF4F4F4.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFFAFAFA.toInt(),
            bg3 = 0xFFEFEFEF.toInt(),
            bg4 = 0xFFE6E6E6.toInt(),
            out1 = 0xFFE7E7E7.toInt(),
            out2 = 0xFFCFCFCF.toInt(),
            text1 = 0xFF141414.toInt(),
            text2 = 0xFF575757.toInt(),
            text3 = 0xFF989898.toInt(),
            accent = 0xFF161616.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF575757.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 墨色 · 深色（OLED 纯黑，与原 MonoDarkColors 逐字段等值） */
        val MonoDark = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF0A0A0A.toInt(),
            bg2 = 0xFF121212.toInt(),
            bg3 = 0xFF1B1B1B.toInt(),
            bg4 = 0xFF252525.toInt(),
            out1 = 0xFF1C1C1C.toInt(),
            out2 = 0xFF2C2C2C.toInt(),
            text1 = 0xFFD0D0D0.toInt(),
            text2 = 0xFF9A9A9A.toInt(),
            text3 = 0xFF6B6B6B.toInt(),
            accent = 0xFFE6E6E6.toInt(),
            onAccent = 0xFF0A0A0A.toInt(),
            accent2 = 0xFF9A9A9A.toInt(),
            onAccent2 = 0xFF0A0A0A.toInt(),
        )

        /** 朱砂 · 昼（与原 VermilionDayColors 逐字段等值） */
        val VermilionDay = ThemeTokens(
            bg0 = 0xFFFAF5F2.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFFBF4F0.toInt(),
            bg3 = 0xFFF1E7E1.toInt(),
            bg4 = 0xFFE9DAD2.toInt(),
            out1 = 0xFFEFE0D9.toInt(),
            out2 = 0xFFDFC4B9.toInt(),
            text1 = 0xFF2B1A16.toInt(),
            text2 = 0xFF6B534B.toInt(),
            text3 = 0xFFA2877D.toInt(),
            accent = 0xFFA32E25.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFFD4786C.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 朱砂 · 夜（OLED 纯黑，与原 VermilionNightColors 逐字段等值） */
        val VermilionNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF140B09.toInt(),
            bg2 = 0xFF1D110E.toInt(),
            bg3 = 0xFF251814.toInt(),
            bg4 = 0xFF2E1D18.toInt(),
            out1 = 0xFF2B1813.toInt(),
            out2 = 0xFF3E241E.toInt(),
            text1 = 0xFFEADAD5.toInt(),
            text2 = 0xFFB79A92.toInt(),
            text3 = 0xFF7F5E56.toInt(),
            accent = 0xFFC04F42.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF8F3B32.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 内置预设入口：调试面板/外观包切换时按 (变体, 明暗) 取默认令牌 */
        fun builtin(variant: ThemeVariant, dark: Boolean): ThemeTokens = when (variant) {
            ThemeVariant.MONO -> if (dark) MonoDark else MonoLight
            ThemeVariant.VERMILION -> if (dark) VermilionNight else VermilionDay
        }
    }
}
