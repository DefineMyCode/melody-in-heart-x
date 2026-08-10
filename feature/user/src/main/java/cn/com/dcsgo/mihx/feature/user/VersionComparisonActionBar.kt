package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.common.time.formatDurationTime

// ─────────────────────────────────────────────────────────────────
// 版本对比页底部操作条：进度拖动 + 播放当前选中版本（A/B 对比聆听）
// ─────────────────────────────────────────────────────────────────

@Composable
internal fun ComparisonActionBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onPlayReference: () -> Unit,
) {
    // 拖动期间用本地值渲染，松手才回写，避免播放位置回跳造成抖动
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val maxDuration = durationMs.coerceAtLeast(1L).toFloat()
    val shownPosition = (dragPosition ?: currentPositionMs.toFloat()).coerceIn(0f, maxDuration)

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (durationMs > 0L) {
                Slider(
                    value = shownPosition,
                    onValueChange = { dragPosition = it },
                    onValueChangeFinished = {
                        dragPosition?.let { onSeekTo(it.toLong()) }
                        dragPosition = null
                    },
                    valueRange = 0f..maxDuration,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatDurationTime(shownPosition.toLong()),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatDurationTime(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPlayReference) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("播放选中")
                }
            }
        }
    }
}
