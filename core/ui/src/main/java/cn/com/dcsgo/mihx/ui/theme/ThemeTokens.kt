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

        /** 靛蓝静夜 · 昼（深海·冷静专注，2026-10-02 新增） */
        val IndigoDay = ThemeTokens(
            bg0 = 0xFFEDF0FB.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFE6EAF8.toInt(),
            bg3 = 0xFFDCE1F4.toInt(),
            bg4 = 0xFFCFD5EE.toInt(),
            out1 = 0xFFE2E6F2.toInt(),
            out2 = 0xFFC3CAE8.toInt(),
            text1 = 0xFF23263B.toInt(),
            text2 = 0xFF5A6390.toInt(),
            text3 = 0xFF9AA0BE.toInt(),
            accent = 0xFF4A5FC0.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF7A8BE8.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 靛蓝静夜 · 夜（深海·冷静专注，2026-10-02 新增） */
        val IndigoNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF0B0D18.toInt(),
            bg2 = 0xFF10131F.toInt(),
            bg3 = 0xFF1A1F33.toInt(),
            bg4 = 0xFF21263F.toInt(),
            out1 = 0xFF23294A.toInt(),
            out2 = 0xFF3A4166.toInt(),
            text1 = 0xFFDCE0F0.toInt(),
            text2 = 0xFFA5ABC8.toInt(),
            text3 = 0xFF646B8E.toInt(),
            accent = 0xFFA6B4F0.toInt(),
            onAccent = 0xFF1C2248.toInt(),
            accent2 = 0xFF5E6FCE.toInt(),
            onAccent2 = 0xFFEEF0FC.toInt(),
        )

        /** 苔藓森野 · 昼（林间·自然疗愈，2026-10-02 新增） */
        val SageDay = ThemeTokens(
            bg0 = 0xFFEFF5EF.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFE9F1E8.toInt(),
            bg3 = 0xFFDFEBDE.toInt(),
            bg4 = 0xFFD2E1D0.toInt(),
            out1 = 0xFFE3EADF.toInt(),
            out2 = 0xFFC3D5BF.toInt(),
            text1 = 0xFF223028.toInt(),
            text2 = 0xFF4F6656.toInt(),
            text3 = 0xFF8CA08C.toInt(),
            accent = 0xFF3F6E52.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF86AD80.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 苔藓森野 · 夜（林间·自然疗愈，2026-10-02 新增） */
        val SageNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF0C120E.toInt(),
            bg2 = 0xFF121A14.toInt(),
            bg3 = 0xFF18231B.toInt(),
            bg4 = 0xFF1F2B22.toInt(),
            out1 = 0xFF223022.toInt(),
            out2 = 0xFF3A4C3A.toInt(),
            text1 = 0xFFDFE8DF.toInt(),
            text2 = 0xFFA7BCA6.toInt(),
            text3 = 0xFF6E836D.toInt(),
            accent = 0xFFA9CBA3.toInt(),
            onAccent = 0xFF1A3520.toInt(),
            accent2 = 0xFF6E9070.toInt(),
            onAccent2 = 0xFFEFF5EC.toInt(),
        )

        /** 琥珀暖忆 · 昼（黄昏·暖光留声，2026-10-02 新增） */
        val AmberDay = ThemeTokens(
            bg0 = 0xFFFBF6EE.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFF8F0E0.toInt(),
            bg3 = 0xFFF2E7CE.toInt(),
            bg4 = 0xFFEBDCB8.toInt(),
            out1 = 0xFFF0E5D0.toInt(),
            out2 = 0xFFE0CBA4.toInt(),
            text1 = 0xFF362B1C.toInt(),
            text2 = 0xFF6B5A42.toInt(),
            text3 = 0xFFA08E6E.toInt(),
            accent = 0xFF9A5B12.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFFD9B06A.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 琥珀暖忆 · 夜（黄昏·暖光留声，2026-10-02 新增） */
        val AmberNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF161008.toInt(),
            bg2 = 0xFF1C1510.toInt(),
            bg3 = 0xFF251D14.toInt(),
            bg4 = 0xFF2E2516.toInt(),
            out1 = 0xFF2C2210.toInt(),
            out2 = 0xFF433620.toInt(),
            text1 = 0xFFF0E6D4.toInt(),
            text2 = 0xFFBDAE8E.toInt(),
            text3 = 0xFF7F704F.toInt(),
            accent = 0xFFEFC583.toInt(),
            onAccent = 0xFF3A2A0A.toInt(),
            accent2 = 0xFFB98A3E.toInt(),
            onAccent2 = 0xFFFFF3DA.toInt(),
        )

        /** 天空澄明 · 昼（晴空·辽阔入海，2026-10-02 新增） */
        val SkyDay = ThemeTokens(
            bg0 = 0xFFEFF5FC.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFE7F0FA.toInt(),
            bg3 = 0xFFDCE7F6.toInt(),
            bg4 = 0xFFCDDCF0.toInt(),
            out1 = 0xFFDFE9F5.toInt(),
            out2 = 0xFFBDD0E8.toInt(),
            text1 = 0xFF1B2A40.toInt(),
            text2 = 0xFF4A628C.toInt(),
            text3 = 0xFF8AA0BC.toInt(),
            accent = 0xFF2F6DB5.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF6FA3E0.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 天空澄明 · 夜（晴空·辽阔入海，2026-10-02 新增） */
        val SkyNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF0B1420.toInt(),
            bg2 = 0xFF0F1B2A.toInt(),
            bg3 = 0xFF152336.toInt(),
            bg4 = 0xFF1B2C44.toInt(),
            out1 = 0xFF1C2E46.toInt(),
            out2 = 0xFF33507A.toInt(),
            text1 = 0xFFDCE8F5.toInt(),
            text2 = 0xFFA3B8D4.toInt(),
            text3 = 0xFF647A9C.toInt(),
            accent = 0xFF7DB8EE.toInt(),
            onAccent = 0xFF12345C.toInt(),
            accent2 = 0xFF4E7FC0.toInt(),
            onAccent2 = 0xFFEAF3FD.toInt(),
        )

        /** 新叶青翠 · 昼（初春·嫩芽破土，2026-10-02 新增） */
        val FreshDay = ThemeTokens(
            bg0 = 0xFFF1F8EE.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFE9F4E5.toInt(),
            bg3 = 0xFFDDEBD9.toInt(),
            bg4 = 0xFFCDE0C8.toInt(),
            out1 = 0xFFE3EFDD.toInt(),
            out2 = 0xFFBFD6B8.toInt(),
            text1 = 0xFF1E2E1E.toInt(),
            text2 = 0xFF4C674C.toInt(),
            text3 = 0xFF8B9C85.toInt(),
            accent = 0xFF327A3B.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFF7DBE72.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 新叶青翠 · 夜（初春·嫩芽破土，2026-10-02 新增） */
        val FreshNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF0E1A0E.toInt(),
            bg2 = 0xFF122112.toInt(),
            bg3 = 0xFF182B18.toInt(),
            bg4 = 0xFF1F361F.toInt(),
            out1 = 0xFF223822.toInt(),
            out2 = 0xFF3A5A38.toInt(),
            text1 = 0xFFE2EFE0.toInt(),
            text2 = 0xFFA9C6A4.toInt(),
            text3 = 0xFF6B8A66.toInt(),
            accent = 0xFFB2DF8F.toInt(),
            onAccent = 0xFF1C3A10.toInt(),
            accent2 = 0xFF6FA463.toInt(),
            onAccent2 = 0xFFEEF8E9.toInt(),
        )

        /** 晨光霞粉 · 昼（破晓·云霞初醒，2026-10-02 新增） */
        val SunriseDay = ThemeTokens(
            bg0 = 0xFFFBF4F1.toInt(),
            bg1 = 0xFFFFFFFF.toInt(),
            bg2 = 0xFFF8ECE8.toInt(),
            bg3 = 0xFFF2E0DB.toInt(),
            bg4 = 0xFFEAD2CC.toInt(),
            out1 = 0xFFF2E2DD.toInt(),
            out2 = 0xFFDFC0B8.toInt(),
            text1 = 0xFF322026.toInt(),
            text2 = 0xFF6A4B52.toInt(),
            text3 = 0xFF9C7780.toInt(),
            accent = 0xFFA8473F.toInt(),
            onAccent = 0xFFFFFFFF.toInt(),
            accent2 = 0xFFE08E82.toInt(),
            onAccent2 = 0xFFFFFFFF.toInt(),
        )

        /** 晨光霞粉 · 夜（破晓·云霞初醒，2026-10-02 新增） */
        val SunriseNight = ThemeTokens(
            bg0 = 0xFF000000.toInt(),
            bg1 = 0xFF170E0E.toInt(),
            bg2 = 0xFF1D1313.toInt(),
            bg3 = 0xFF261818.toInt(),
            bg4 = 0xFF301E1E.toInt(),
            out1 = 0xFF311D1C.toInt(),
            out2 = 0xFF4A2A28.toInt(),
            text1 = 0xFFF3E2E0.toInt(),
            text2 = 0xFFC2A09E.toInt(),
            text3 = 0xFF87605F.toInt(),
            accent = 0xFFF2A6A0.toInt(),
            onAccent = 0xFF3A1210.toInt(),
            accent2 = 0xFFC26A63.toInt(),
            onAccent2 = 0xFFFDEBEA.toInt(),
        )

        /** 内置预设入口：调试面板/外观包切换时按 (变体, 明暗) 取默认令牌 */
        fun builtin(variant: ThemeVariant, dark: Boolean): ThemeTokens = when (variant) {
            ThemeVariant.MONO -> if (dark) MonoDark else MonoLight
            ThemeVariant.VERMILION -> if (dark) VermilionNight else VermilionDay
            ThemeVariant.INDIGO -> if (dark) IndigoNight else IndigoDay
            ThemeVariant.SAGE -> if (dark) SageNight else SageDay
            ThemeVariant.AMBER -> if (dark) AmberNight else AmberDay
            ThemeVariant.SKY -> if (dark) SkyNight else SkyDay
            ThemeVariant.FRESH -> if (dark) FreshNight else FreshDay
            ThemeVariant.SUNRISE -> if (dark) SunriseNight else SunriseDay
        }
    }
}
