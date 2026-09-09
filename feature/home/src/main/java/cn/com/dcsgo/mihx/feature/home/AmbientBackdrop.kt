package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 主屏氛围背景（方案D「此刻」）—— 封面下段取色纯色填充。
 *
 * 网易云思路：背景色直接取自封面（下段区域平均色），纯色填充不渐变，
 * 与封面色调天然融洽。无封面时退化为主题色。
 */
@Composable
fun AmbientBackdrop(
    albumArtUri: android.net.Uri? = null,
    /** 封面主色（可选）。无封面时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()

    // 封面下段取色（全像素平均，不过滤极值）
    val coverColors = rememberCoverColors(albumArtUri)
    val bgColor = (coverColors?.bottom ?: accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.45f) else darken(c, 0.55f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        content()
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
