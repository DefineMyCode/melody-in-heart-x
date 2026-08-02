package cn.com.dcsgo.mihx.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale (UI 设计定稿): 沿用 Material 3 默认字阶，仅保留 [bodyLarge] 的现有定制
 * (16sp / 24 / Normal / 0.5sp)，不新增覆写 —— 中性设计以克制为准。
 */
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
