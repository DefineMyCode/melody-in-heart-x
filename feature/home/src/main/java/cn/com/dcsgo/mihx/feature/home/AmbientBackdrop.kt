package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 主屏氛围背景（方案D「此刻」）—— 网易云式「封面背光」。
 *
 * 参考网易云实现拆解出的三要素：
 * 1. 光晕以封面为中心包裹（径向光中心≈封面中心略偏上），封面像被自己照亮
 * 2. 光色 = 封面主色的加深提饱和版（单色系，不引入第二色相冲淡封面）
 * 3. 亮度峰紧贴封面边缘，向下渐灭到底部近黑
 *
 * 实现：BoxWithConstraints 感知屏宽，径向光 center 定在 (屏宽/2, 封面中心y)。
 * y 坐标由 [glowCenterYFraction]（屏高比例）传入——主屏封面约占 0.22 高度处。
 */
@Composable
fun AmbientBackdrop(
    /** 封面主色（可选）。null 时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    /** 径向光中心的纵向位置（占屏高比例，主屏封面中心约 0.24） */
    glowCenterYFraction: Float = 0.24f,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    // 网易云式取色：主色加深+提饱和，让它像"封面的延续"而不是浮着的彩色灯
    val glow = (accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.55f) else darken(c, 0.35f) }
    val base = MaterialTheme.colorScheme.background

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val center = Offset(widthPx / 2f, heightPx * glowCenterYFraction)
        // 半径≈屏宽 1.1 倍:光晕包裹封面并延伸到两侧边缘外一点
        val radius = widthPx * 1.1f

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 层1: 封面背光径向光晕(峰值贴封面边缘)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = if (dark) 0.95f else 0.9f),
                            glow.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = radius,
                    ),
                )
                // 层2: 中部余晖——同色低透明度线性拖尾,避免径向光之外死黑断层
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glow.copy(alpha = if (dark) 0.28f else 0.22f),
                            Color.Transparent,
                        ),
                        startY = heightPx * 0.15f,
                        endY = heightPx * 0.85f,
                    ),
                ),
        ) {
            content()
        }
    }
}

/** 加深并提饱和：返回 HSL 上降低亮度、拉满一点饱和度的颜色。 */
private fun darken(color: Color, luminanceFactor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv,
    )
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f) // 饱和度提升
    hsv[2] *= luminanceFactor                   // 亮度压暗
    return Color(android.graphics.Color.HSVToColor(hsv))
}
