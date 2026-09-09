package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 主屏氛围背景（方案D「此刻」）—— 网易云式「封面上下分区取色渐变」。
 *
 * 网易云实测要点：
 * 1. 顶部背景 = 封面上段取色（衔接封面上部色调）
 * 2. 底部背景 = 封面下段取色（承接封面暗部），垂直渐变过渡
 * 3. 封面下缘羽化渐隐融入背景，无生硬分界线
 * 4. 文字用白色系（不同透明度分层），在取色背景上可读性好
 */
@Composable
fun AmbientBackdrop(
    albumArtUri: android.net.Uri? = null,
    /** 封面主色（可选）。无封面时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()

    // 封面上下分区取色（全像素平均，不过滤极值）
    val coverColors = rememberCoverColors(albumArtUri)
    val topColor = (coverColors?.top ?: accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.55f) else darken(c, 0.72f) }
    val bottomColor = (coverColors?.bottom ?: coverColors?.top ?: accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.30f) else darken(c, 0.42f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        // 封面为方形全出血: 下缘在 y=屏宽处。渐变: 顶部=上段色 → 封面下缘=下段色 → 底部=下段色加深
        val coverBottom = widthPx.coerceAtMost(heightPx)
        val deepBottom = bottomColor.let { c -> if (dark) darken(c, 0.7f) else darken(c, 0.72f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(topColor, bottomColor, deepBottom),
                        startY = 0f,
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
