package cn.com.dcsgo.mihx.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp

/**
 * 播放中 EQ 指示器（对齐 UI 设计系统 §5.13 / §6.5）
 *
 * 4 根竖线以不同相位交替伸缩，表示当前正在播放。
 * 用 [MaterialTheme.colorScheme.primary]（accent）着色。
 *
 * @param modifier 修饰符
 */
@Composable
fun EqualizerIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = 4
    // 每根竖线一个独立的伸缩动画（不同延迟相位）
    val phases = List(bars) { index ->
        transition.animateFloat(
            initialValue = 0.26f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 720,
                    delayMillis = index * 150,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "eq_bar_$index",
        )
    }

    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(16.dp)) {
        val gap = size.width / (bars * 2f - 1f)
        val barWidth = gap
        val maxHeight = size.height
        phases.forEachIndexed { index, phase ->
            val height = maxHeight * phase.value
            val left = index * (barWidth + gap)
            val top = (maxHeight - height) / 2f
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
