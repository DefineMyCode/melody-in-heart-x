package cn.com.dcsgo.mihx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字体系统（对齐 UI 设计文档 §3.2）
 *
 * | 层级 | 字号 | 字重 | 用途 |
 * |------|------|------|------|
 * | Display | 34 / 800 | 品牌/大标题 |
 * | Title Large | 22 / 700 | 页面标题 |
 * | Title Medium | 16 / 600 | 卡片标题、歌单名 |
 * | Body Medium | 14 / 400 | 正文、列表项 |
 * | Body Small | 12 / 400 | 辅助信息、艺术家 |
 * | Label | 11 / 600 | 徽章、时间戳 |
 * | Lyric Active | 20 / 800 | 当前歌词高亮行（见 [LyricActive]） |
 */
val Typography = Typography(
    // 品牌/大标题
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    // 页面标题
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // 卡片标题、歌单名
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    // 正文、列表项（Material 默认）
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    // 辅助信息、艺术家
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    // 徽章、时间戳
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

/**
 * 当前歌词高亮行：20sp / 800 粗体（设计文档 §3.2 Lyric Active）。
 * 由 [LyricsView] 组合使用，随用户字号缩放倍率调整。
 */
val LyricActive = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 32.sp,
    letterSpacing = 0.sp,
)

/** 数字/时间等宽字体样式（设计文档 §3.1 --font-num），用于时间码、计数、版本号 */
val NumericText = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
)
