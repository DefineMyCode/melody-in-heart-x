package cn.com.dcsgo.mihx.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cn.com.dcsgo.mihx.core.model.EmotionGroup
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import kotlin.math.abs
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────
// 歌曲情绪区（整曲 V/A 曲线 + 中文词条 + 用户校准）
// 底层 V-A 连续坐标, 表层 40 词中文词表(10 组×4)映射(分层双轨).
// 用户校准值优先于模型值; 未校准歌由端侧 kNN 注入 userValence/Arousal.
// ─────────────────────────────────────────────────────────────────

/** 滑动平均平滑逐窗曲线(窗口 5, 边界缩窗), 消除单窗噪声. */
fun smoothCurve(curve: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
    if (curve.size < 3) return curve
    val w = 5
    return curve.indices.map { i ->
        val lo = (i - w / 2).coerceAtLeast(0)
        val hi = (i + w / 2).coerceAtMost(curve.lastIndex)
        val seg = curve.subList(lo, hi + 1)
        seg.map { it.first }.average().toFloat() to
            seg.map { it.second }.average().toFloat()
    }
}

/** A 曲线峰值须高出均值这么多才算"真高潮"(20 首实测分布定标: 平缓/全程高能歌 ≤0.49, 真高潮歌 ≥0.52). */
private const val PEAK_MARGIN = 0.5f

/** 高潮是否显著: A 最大值显著高于 A 均值. */
fun hasSignificantPeak(curve: List<Pair<Float, Float>>): Boolean {
    if (curve.size < 4) return false
    val a = curve.map { it.second }
    return a.max() > a.average().toFloat() + PEAK_MARGIN
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongEmotionSection(
    emotion: SongEmotion?,
) {
    if (emotion == null || emotion.curve.size < 2) {
        Text(
            text = "情绪分析：尚未完成（充电时自动分析曲库）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    val vColor = Color(0xFF42A5F5)
    val aColor = Color(0xFFFFB74D)
    val peakColor = Color(0xFFE57373)
    // 保存走 suspend 控制器(仓库桥在 Default 线程执行), 用组合作用域拉起
    val scope = rememberCoroutineScope()
    var showCalibrate by remember { mutableStateOf(false) }
    // 保存成功后本地覆盖展示(对话框内立即可见), 下次打开从库重载
    val controller = LocalEmotionCorrectionController.current
    var overrideEmotion by remember(emotion) { mutableStateOf<SongEmotion?>(null) }
    val shown = overrideEmotion ?: emotion
    // 词条行(主展示) — 弹窗"原有"排也复用
    val tags = emotionTagsOf(shown)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "情绪",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (tags.isEmpty()) {
                    Text(
                        text = "未定",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { t ->
                            AssistChip(
                                onClick = {},
                                label = { Text(t, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                }
                if (shown.userTags.isNotEmpty()) {
                    Text(
                        text = "已由你校准",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
            if (controller != null) {
                TextButton(onClick = { showCalibrate = true }) {
                    Text(
                        text = if (shown.userTags.isNotEmpty()) "修改" else "不像？标记",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(
                    scheme.surfaceVariant.copy(alpha = 0.5f),
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val smoothed = smoothCurve(emotion.curve)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val mid = h / 2f
                val half = mid - 4f
                // 基准线: 与歌曲属性文字同色(onSurfaceVariant), 深浅主题都可读
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(10f, 10f)
                )
                val refColor = scheme.onSurfaceVariant
                fun drawRef(y: Float, alpha: Float, widthDp: Float) {
                    val p = Path().apply {
                        moveTo(0f, y)
                        lineTo(w, y)
                    }
                    drawPath(
                        p,
                        refColor.copy(alpha = alpha),
                        style = Stroke(width = widthDp.dp.toPx(), pathEffect = dash),
                    )
                }
                drawRef(mid - half * 0.5f, 0.28f, 1f)
                drawRef(mid + half * 0.5f, 0.28f, 1f)
                drawRef(mid, 0.75f, 1.2f)
                fun drawSeries(get: (Pair<Float, Float>) -> Float, color: Color) {
                    val path = Path()
                    val dx = if (smoothed.size > 1) w / (smoothed.size - 1) else 0f
                    smoothed.forEachIndexed { i, p ->
                        val x = i * dx
                        val y = mid - get(p).coerceIn(-1f, 1f) * (mid - 4f)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
                }
                drawSeries({ it.first }, vColor)
                drawSeries({ it.second }, aColor)
                // A 峰标记: 峰值显著高于均值才画(平缓歌的噪声尖不标)
                if (
                    emotion.durationSec > 0f && emotion.windowsAnalyzed > 0 &&
                    hasSignificantPeak(emotion.curve)
                ) {
                    val peakX = (emotion.peakSec / emotion.durationSec)
                        .coerceIn(0f, 1f) * w
                    val peakIdx = (emotion.peakSec / 2.5f).toInt()
                        .coerceIn(0, smoothed.lastIndex)
                    val peakY = mid - smoothed[peakIdx].second.coerceIn(-1f, 1f) * (mid - 4f)
                    drawCircle(peakColor, radius = 5.dp.toPx(), center = Offset(peakX, peakY))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "蓝=冷暖(参考) 橙=能量 红点=高潮",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            val m = emotion.peakSec.toInt()
            Text(
                text = if (hasSignificantPeak(emotion.curve)) {
                    "高潮 %02d:%02d".format(m / 60, m % 60)
                } else {
                    "高潮：不明显"
                },
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }

    if (showCalibrate && controller != null) {
        EmotionCalibrateDialog(
            // 打开时预勾选当前展示标签: 用户可点掉/增补, 上限 4
            initial = tags.toSet(),
            originals = tags,
            hasUserTags = shown.userCorrected,
            onDismiss = { showCalibrate = false },
            onConfirm = { words ->
                scope.launch {
                    if (controller.save(shown.songId, words)) {
                        // 空词 = 恢复自动: 本地覆盖立即回到曲线词
                        overrideEmotion = if (words.isEmpty()) {
                            shown.copy(
                                userTags = emptyList(),
                                userValence = null,
                                userArousal = null,
                            )
                        } else {
                            shown.copy(userTags = words.toList())
                        }
                    }
                    showCalibrate = false
                }
            },
        )
    }
}

/**
 * 校准对话框: 10 组 × 4 中文词分组展示, 多选上限 4.
 * 选中态必须用 SnapshotStateList——mutableSetOf 包 mutableStateOf 不触发重组(bug 教训).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EmotionCalibrateDialog(
    /** 打开时预选中的词(= 已手动校准过的词) */
    initial: Set<String>,
    /** 这首歌当前展示的标签(自动投票或校准结果), "原有"排 */
    originals: List<String>,
    /** 是否已有用户标记(决定"恢复自动"出口是否可见) */
    hasUserTags: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    // 选中态挂在歌身份上 remember: 弹窗开着时父级重组不能重置用户进行中的选择
    val selected: SnapshotStateList<String> = remember(initial) { initial.toMutableStateList() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 高度上限先限定, 中间词条区再滚动, 底部按钮固定永远可见
                // (教训: verticalScroll 在 heightIn 之前会把内容按无限高测量, 滚动范围=0 直接裁掉按钮)
                .heightIn(max = 600.dp)
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(20.dp),
                )
                .padding(20.dp),
        ) {
            Text(
                text = "这首歌听起来是…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "最多选 ${EmotionGroup.MAX_TAGS} 个词，已记录你的标记，这首歌将显示你选择的词条",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 固定区(不随词条滚动): 已选 + 原有, 点按即移除/恢复, 免翻词海
            val toggle: (String) -> Unit = { word ->
                if (word in selected) {
                    selected.remove(word)
                } else if (selected.size < EmotionGroup.MAX_TAGS) {
                    selected.add(word)
                }
            }
            ChipLabelRow(label = "已选 (点按移除)") {
                if (selected.isEmpty()) {
                    Text(
                        text = "还没选词",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                selected.forEach { word ->
                    InputChip(
                        selected = true,
                        onClick = { toggle(word) },
                        label = { Text(word) },
                    )
                }
            }
            if (originals.isNotEmpty()) {
                ChipLabelRow(label = "原有 (点按移除/恢复)") {
                    originals.forEach { word ->
                        InputChip(
                            selected = word in selected,
                            onClick = { toggle(word) },
                            label = { Text(word) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EmotionGroup.entries.forEach { group ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        group.words.forEach { word ->
                            FilterChip(
                                selected = word in selected,
                                onClick = {
                                    if (word in selected) {
                                        selected.remove(word)
                                    } else if (selected.size < EmotionGroup.MAX_TAGS) {
                                        selected.add(word)
                                    }
                                },
                                label = { Text(word) },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 该歌已有用户标记时, 提供"恢复自动"出口: 空词保存即清标记
                if (hasUserTags) {
                    TextButton(onClick = { onConfirm(emptySet()) }) { Text("恢复自动") }
                }
                Row(horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = { onConfirm(selected.toSet()) },
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

/** 校准弹窗顶部固定区一行: 小标签 + chips(可换行). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipLabelRow(
    label: String,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}
