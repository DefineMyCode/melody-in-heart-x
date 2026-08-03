package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.ui.graphics.Color

internal data class ThemePalette(
    val bg0: Color,       // 页面底色
    val bg1: Color,       // 表面/卡片
    val bg2: Color,       // 列表项/输入框
    val bg3: Color,       // 浮层
    val bg4: Color,       // 底部栏/弹窗
    val out1: Color,      // 分隔线
    val out2: Color,      // 描边
    val text1: Color,     // 主文字
    val text2: Color,     // 次级文字
    val text3: Color,     // 弱化文字
    val accent: Color,    // 强调
    val onAccent: Color,  // 强调上的内容
    val accent2: Color,   // 次强调
    val onAccent2: Color, // 次强调上的内容
)

/** 墨色 · 浅色（黑白灰） */
internal val MonoLightColors = ThemePalette(
    bg0 = Color(0xFFF4F4F4),
    bg1 = Color(0xFFFFFFFF),
    bg2 = Color(0xFFFAFAFA),
    bg3 = Color(0xFFEFEFEF),
    bg4 = Color(0xFFE6E6E6),
    out1 = Color(0xFFE7E7E7),
    out2 = Color(0xFFCFCFCF),
    text1 = Color(0xFF141414),
    text2 = Color(0xFF575757),
    text3 = Color(0xFF989898),
    accent = Color(0xFF161616),
    onAccent = Color(0xFFFFFFFF),
    accent2 = Color(0xFF575757),
    onAccent2 = Color(0xFFFFFFFF),
)

/** 墨色 · 深色（OLED 纯黑背景，近白的暗文字） */
internal val MonoDarkColors = ThemePalette(
    bg0 = Color(0xFF000000),
    bg1 = Color(0xFF0A0A0A),
    bg2 = Color(0xFF121212),
    bg3 = Color(0xFF1B1B1B),
    bg4 = Color(0xFF252525),
    out1 = Color(0xFF1C1C1C),
    out2 = Color(0xFF2C2C2C),
    text1 = Color(0xFFD0D0D0),
    text2 = Color(0xFF9A9A9A),
    text3 = Color(0xFF6B6B6B),
    accent = Color(0xFFE6E6E6),
    onAccent = Color(0xFF0A0A0A),
    accent2 = Color(0xFF9A9A9A),
    onAccent2 = Color(0xFF0A0A0A),
)

/** 朱砂 · 心有乐章 · 昼 */
internal val VermilionDayColors = ThemePalette(
    bg0 = Color(0xFFFAF5F2),
    bg1 = Color(0xFFFFFFFF),
    bg2 = Color(0xFFFBF4F0),
    bg3 = Color(0xFFF1E7E1),
    bg4 = Color(0xFFE9DAD2),
    out1 = Color(0xFFEFE0D9),
    out2 = Color(0xFFDFC4B9),
    text1 = Color(0xFF2B1A16),
    text2 = Color(0xFF6B534B),
    text3 = Color(0xFFA2877D),
    accent = Color(0xFFA32E25),
    onAccent = Color(0xFFFFFFFF),
    accent2 = Color(0xFFD4786C),
    onAccent2 = Color(0xFFFFFFFF),
)

/** 朱砂 · 心有乐章 · 夜（OLED 纯黑） */
internal val VermilionNightColors = ThemePalette(
    bg0 = Color(0xFF000000),
    bg1 = Color(0xFF140B09),
    bg2 = Color(0xFF1D110E),
    bg3 = Color(0xFF251814),
    bg4 = Color(0xFF2E1D18),
    out1 = Color(0xFF2B1813),
    out2 = Color(0xFF3E241E),
    text1 = Color(0xFFEADAD5),
    text2 = Color(0xFFB79A92),
    text3 = Color(0xFF7F5E56),
    accent = Color(0xFFC04F42),
    onAccent = Color(0xFFFFFFFF),
    accent2 = Color(0xFF8F3B32),
    onAccent2 = Color(0xFFFFFFFF),
)
