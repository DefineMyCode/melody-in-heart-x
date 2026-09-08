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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext

/**
 * 主屏氛围背景（方案D「此刻」）—— 网易云式「封面背光」。
 *
 * 三要素（对齐网易云实测效果）：
 * 1. 模糊封面位图铺满全屏当底（网易云的本质做法，光斑随封面内容走）
 * 2. 封面中心径向光晕提亮封面周围
 * 3. 全屏保持封面色系到底，只做「上亮下暗」渐变——不收黑
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
    val context = LocalContext.current
    val base = MaterialTheme.colorScheme.background
    // 主色加深但保亮度(网易云是中亮度): 暗 0.78 而非 0.55
    val glow = (accentFromCover ?: MaterialTheme.colorScheme.primaryContainer)
        .let { c -> if (dark) darken(c, 0.78f) else darken(c, 0.62f) }
    // 渐变终点 = glow 再暗一档(网易云底部是深苔绿,不是黑)
    val bottom = darken(glow, 0.62f)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }

        // 层0: 模糊封面铺满全屏(网易云本质做法)——Compose blur(硬件加速,minSdk 33)
        if (albumArtUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(albumArtUri)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(40.dp),
                contentScale = ContentScale.Crop,
                alpha = if (dark) 0.9f else 0.85f,
            )
            // 模糊封面之上压一层主色薄纱统一色调
            Box(
                Modifier
                    .fillMaxSize()
                    .background(glow.copy(alpha = 0.45f)),
            )
        } else {
            // 无封面: 主题色渐变兜底
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(glow.copy(alpha = 0.6f), bottom)),
                    ),
            )
        }

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

        // 层2: 上亮下暗整体渐变——终点为深色同色系,永不收黑
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            base.copy(alpha = 0.15f),
                            bottom.copy(alpha = 0.85f),
                        ),
                        startY = heightPx * 0.35f,
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
