package cn.com.dcsgo.mihx.app.tuning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * UI 参数调试面板（仅 debug 构建可达）。
 *
 * 打开方式：我的页长按「版本」行。
 * 拖动滑条 → 直接改写 UiTuning → 经 CompositionLocal 实时生效（无需重启、无需重新构建）。
 * 「导出」写出 ui-tuning.json —— 阶段 1 外观包的格式雏形。
 */
@Composable
fun UiTuningPanel(
    tuning: UiTuning,
    onTuningChange: (UiTuning) -> Unit,
    onExport: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("UI 参数调试", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    TextButton(onClick = onExport) { Text("导出") }
                    TextButton(onClick = onReset) { Text("重置") }
                    TextButton(onClick = onClose) { Text("关闭") }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                GroupTitle("歌曲列表行")
                SliderRow("行垂直内边距", tuning.vis.listRowVerticalPaddingDp, 0f..24f) {
                    onTuningChange(tuning.copy(vis = tuning.vis.copy(listRowVerticalPaddingDp = it)))
                }
                SliderRow("缩略图边长", tuning.vis.listCoverSizeDp, 32f..72f) {
                    onTuningChange(tuning.copy(vis = tuning.vis.copy(listCoverSizeDp = it)))
                }
                SliderRow("缩略图圆角", tuning.vis.listCoverCornerDp, 0f..24f) {
                    onTuningChange(tuning.copy(vis = tuning.vis.copy(listCoverCornerDp = it)))
                }

                GroupTitle("播放面板（首页）")
                SliderRow("封面边长", tuning.panel.coverSizeDp, 96f..320f) {
                    onTuningChange(tuning.copy(panel = tuning.panel.copy(coverSizeDp = it)))
                }
                SliderRow("封面圆角", tuning.panel.coverCornerDp, 0f..40f) {
                    onTuningChange(tuning.copy(panel = tuning.panel.copy(coverCornerDp = it)))
                }

                GroupTitle("区块间距")
                SliderRow("区块纵向间距", tuning.spacing.sectionSpacingDp, 0f..48f) {
                    onTuningChange(tuning.copy(spacing = tuning.spacing.copy(sectionSpacingDp = it)))
                }

                GroupTitle("底部迷你播放条")
                SliderRow("封面边长", tuning.mini.coverSizeDp, 28f..64f) {
                    onTuningChange(tuning.copy(mini = tuning.mini.copy(coverSizeDp = it)))
                }
                SliderRow("封面圆角", tuning.mini.coverCornerDp, 0f..20f) {
                    onTuningChange(tuning.copy(mini = tuning.mini.copy(coverCornerDp = it)))
                }
                SliderRow("进度条粗细", tuning.mini.progressHeightDp, 1f..8f) {
                    onTuningChange(tuning.copy(mini = tuning.mini.copy(progressHeightDp = it)))
                }
                SliderRow("播放按钮直径", tuning.mini.playButtonSizeDp, 24f..56f) {
                    onTuningChange(tuning.copy(mini = tuning.mini.copy(playButtonSizeDp = it)))
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "当前值:\n" + tuning.summary(),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** 滑条 + 实时数值显示（数值可直接抄走写回代码默认值） */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp)
            Text("%.1f".format(local), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
        Slider(
            value = local,
            valueRange = range,
            onValueChange = {
                local = it
                onChange(it)
            },
        )
    }
}

/** 精简的可粘贴数值摘要 */
fun UiTuning.summary(): String = buildString {
    append("vis=").append(vis.listRowVerticalPaddingDp).append("/")
        .append(vis.listCoverSizeDp).append("/").append(vis.listCoverCornerDp).append('\n')
    append("panel=").append(panel.coverSizeDp).append("/")
        .append(panel.coverCornerDp).append('\n')
    append("spacing=").append(spacing.sectionSpacingDp).append('\n')
    append("mini=").append(mini.coverSizeDp).append("/")
        .append(mini.coverCornerDp).append("/").append(mini.progressHeightDp).append("/")
        .append(mini.playButtonSizeDp)
}
