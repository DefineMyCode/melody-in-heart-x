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

/**
 * 主屏氛围背景（方案D「此刻」）—— 网易云式「封面取色渐变」。
 *
 * 核心思路（对齐网易云）：背景不是模糊封面，而是**封面上下两段的取色**——
 * 1. 封面以上区域 = 封面上段取色（与封面顶部融洽衔接）
 * 2. 封面区域本身 = 图片，无需背景
 * 3. 封面以下 = 从封面下段取色渐变加深，融洽过渡到底部
 * 避免模糊铺底造成的"整页一坨糊"，也避免单一主色与封面上下色调脱节。
 */
@Composable
fun AmbientBackdrop(
    albumArtUri: android.net.Uri? = null,
    /** 封面主色（可选）。无封面时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    /** 径向光中心的纵向位置（占屏高比例，主屏圆形封面中心约 0.32） */
    glowCenterYFraction: Float = 0.32f,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()

    // 封面上下取色：上段用于顶部衔接，下段用于底部渐变终点
    val coverColors = rememberCoverColors(albumArtUri)
    val topColor = (coverColors?.top ?: accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.70f) else darken(c, 0.82f) }
    val bottomColor = (coverColors?.bottom ?: coverColors?.top ?: accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.45f) else darken(c, 0.55f) }
    val glow = topColor

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // 层0: 封面取色双色渐变（上段色→下段色），无模糊图
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(topColor, bottomColor)),
                ),
        )

        // 层1: 封面径向提亮光晕
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        center = Offset(widthPx / 2f, heightPx * glowCenterYFraction),
                        radius = widthPx * 1.1f,
                    ),
                ),
        )

        // 层2: 封面下缘以下压暗渐变——保证信息区文字可读,色相与封面下段一致
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bottomColor.copy(alpha = 0.55f),
                        ),
                        startY = heightPx * 0.42f,
                        endY = heightPx,
                    ),
                ),
        ) {
            content()
        }
    }
}

/** HSV 降亮度提饱和。 */
private fun darken(color: Color, luminanceFactor: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
        hsv,
    )
    hsv[1] = (hsv[1] * 1.2f).coerceAtMost(1f)
    hsv[2] *= luminanceFactor
    return Color(android.graphics.Color.HSVToColor(hsv))
}
