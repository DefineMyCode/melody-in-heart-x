package cn.com.dcsgo.mihx.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 定位高亮状态。
 *
 * 记录最后一次「定位当前播放」的目标 id（歌曲 id 或分组 key），
 * 以及触发次数。列表项通过 [Modifier.locateHighlightFlash] 观察该状态，
 * 当自己的 id 与目标匹配时播放一次背景高亮动画。
 */
@Stable
class LocateHighlightState {
    var targetId: Any? by mutableStateOf(null)
        private set
    var trigger: Int by mutableStateOf(0)
        private set

    /** 触发一次定位高亮，目标 id 由调用方传入（歌曲 id / 分组 key）。 */
    fun trigger(id: Any?) {
        targetId = id
        trigger++
    }
}

@Composable
fun rememberLocateHighlightState(): LocateHighlightState = remember { LocateHighlightState() }

/**
 * 定位当前播放后的背景高亮动画。
 *
 * 当 [state] 被触发且 [id] 与目标匹配时，绘制一层使用主题 [MaterialTheme.colorScheme.primary]
 * 的圆角高亮，快速出现后缓慢淡出。适用于列表项外层修饰符，与主题自动适配。
 *
 * @param id           列表项的唯一标识（歌曲 id 或分组 key）
 * @param state        定位高亮状态
 * @param cornerRadius 高亮圆角半径
 */
@Composable
fun Modifier.locateHighlightFlash(
    id: Any?,
    state: LocateHighlightState,
    cornerRadius: Dp = 8.dp,
): Modifier {
    val color = MaterialTheme.colorScheme.primary
    val animatable = remember(id) { Animatable(0f) }
    val isTarget = state.targetId == id

    LaunchedEffect(state.trigger) {
        if (isTarget) {
            animatable.snapTo(0f)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 140, easing = LinearOutSlowInEasing),
            )
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            )
        }
    }

    return this.drawWithContent {
        drawContent()
        if (animatable.value > 0f) {
            drawRoundRect(
                color = color.copy(alpha = animatable.value * 0.32f),
                cornerRadius = CornerRadius(cornerRadius.toPx()),
            )
        }
    }
}
