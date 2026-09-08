package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 主屏氛围背景（方案D「此刻」）。
 *
 * 三层叠加（跟随封面主色，无封面退化主题色）：
 * 1. 顶部强光：主色径向渐变（上方中点，半径大）
 * 2. 整体对角渐变：主色→次色→背景，保证中部仍有色感
 * 3. 底部自然收黑：由第 2 层 endY 控制落点
 *
 * "更明显丰富"：不再用 0.35f 的薄纱 alpha——主色提到 0.85（顶部核心区接近实色），
 * 并引入 vibrant 提供第二色相，避免单色渐变的单调。
 */
@Composable
fun AmbientBackdrop(
    /** 封面主色（可选）。null 时退化为主题色氛围。 */
    accentFromCover: Color? = null,
    /** 封面次色（vibrant，可选），与主色构成双色渐变 */
    secondaryFromCover: Color? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val primary = accentFromCover ?: MaterialTheme.colorScheme.primaryContainer
    val secondary = secondaryFromCover ?: primary.copy(
        // 无次色时给主色一点明度偏移,避免双层同色死板
        red = (primary.red + 0.08f).coerceAtMost(1f),
        green = primary.green,
        blue = (primary.blue * 1.15f).coerceAtMost(1f),
    )
    val base = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 层1: 顶部径向强光
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        if (dark) primary.copy(alpha = 0.85f) else primary.copy(alpha = 0.9f),
                        Color.Transparent,
                    ),
                    center = Offset(x = Float.POSITIVE_INFINITY / 2f, y = -200f),
                    radius = 1400f,
                ),
            )
            // 层2: 对角主色→次色→背景
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        if (dark) secondary.copy(alpha = 0.55f) else secondary.copy(alpha = 0.7f),
                        base,
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY,
                ),
            ),
    ) {
        content()
    }
}
