package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 主屏氛围背景（方案D「此刻」落地）。
 *
 * ponytail: 设计稿用「封面主色提取 → 径向渐变」。那是新依赖(palette) + 异步取色 +
 * 缓存失效一整套；先用主题色静态渐变占位——朱砂主题天然泛红、墨色天然中性，
 * 视觉同源且零成本。真要跟封面联动时再上 Palette API。
 */
@Composable
fun AmbientBackdrop(
    /** 封面主色（可选）。null 时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    // 主题容器色 → 背景色的对角渐变：播放主屏的"氛围光"底
    val glow = accentFromCover ?: MaterialTheme.colorScheme.primaryContainer
    val base = MaterialTheme.colorScheme.background
    val brush = Brush.verticalGradient(
        colors = listOf(
            glow.copy(alpha = if (dark) 0.35f else 0.55f),
            base,
        ),
        startY = 0f,
        endY = Float.POSITIVE_INFINITY,
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush),
    ) {
        content()
    }
}
