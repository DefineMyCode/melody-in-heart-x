package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.domain.playback.MoodPoolStats
import cn.com.dcsgo.mihx.domain.playback.MoodSlotPolicy

/**
 * 编辑时段弹层（新增 / 编辑共用）。
 *
 * 字段与说明文案对照设计文档 §5 文案表；重叠/零长度校验由
 * MoodSlotResolver.validate 在保存时执行，冲突信息以 message 返回。
 * 时间选择用系统 TimePicker（跟随系统 12/24h 制），存储统一换算为当日分钟数。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodSlotEditDialog(
    editing: TimeSlotConfig?,           // null = 新增
    existingConfigs: List<TimeSlotConfig>,
    tagCounts: Map<String, Int>,
    /**
     * 池子三层统计：包含词条匹配数 / 其中被排除数 / 最终可随机数。
     *
     * 由调用方用与播放时同一套判定提供，保证界面数字 = 实际会播到的歌数。
     */
    poolStatsOf: (Set<String>, Set<String>) -> MoodPoolStats,
    librarySize: Int,
    availableTags: List<String>,        // 全部可选词条（自动分析可用）
    onDismiss: () -> Unit,
    onSave: (TimeSlotConfig) -> Unit,
) {
    val startMinutes = editing?.startMinutes ?: 22 * 60
    val endMinutes = editing?.endMinutes ?: 6 * 60

    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var start by remember { mutableStateOf(startMinutes) }
    var end by remember { mutableStateOf(endMinutes) }
    var tags by remember { mutableStateOf(editing?.tags?.toSet().orEmpty()) }
    var excludedTags by remember { mutableStateOf(editing?.excludedTags?.toSet().orEmpty()) }
    var pickingTime by remember { mutableStateOf<String?>(null) } // "start" | "end"
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val resolver = remember { cn.com.dcsgo.mihx.domain.playback.MoodSlotResolver() }
    val candidate = TimeSlotConfig(
        id = editing?.id ?: System.currentTimeMillis(),
        name = name,
        startMinutes = start,
        endMinutes = end,
        tags = tags.toList(),
        excludedTags = excludedTags.toList(),
    )
    // 即时校验（输入时反馈，而非保存后报错）：名称非空 + 包含/排除至少一个 + 端点合法
    val localError = when {
        name.isBlank() -> "请填写时段名"
        tags.isEmpty() && excludedTags.isEmpty() -> "请至少选择包含或排除词条"
        tags.any { it in excludedTags } -> "排除词条不能与包含词条相同"
        start == end -> "开始与结束时间不能相同"
        else -> null
    }
    val conflict = errorMessage // 保存时由 resolver 返回的冲突信息

    // 评审 2026-10-27 · M-15：记忆化。
    // 原先每次重组都重算，而 `name` 绑在 OutlinedTextField 上 → **每敲一个字**
    // 就做一次全库匹配扫描。现在只在词条集合变化时重算（打字不改变词条 → 不重算）。
    val poolStats = remember(tags, excludedTags) {
        if (tags.isEmpty() && excludedTags.isEmpty()) {
            MoodPoolStats(0, 0, 0)
        } else {
            // 与播放时判定完全一致: 包含命中再剔除排除词条
            poolStatsOf(tags, excludedTags)
        }
    }


    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(22.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = if (editing == null) "添加时段" else "编辑时段",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            // ── 时段名 ──
            Spacer(Modifier.height(14.dp))
            FieldLabel("时段名")
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { name = it; errorMessage = null },
                placeholder = { Text("如：深夜静谧 / 通勤提神 / 午后小憩", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = name.length > cn.com.dcsgo.mihx.domain.playback.MoodSlotPolicy.NAME_MAX_LENGTH,
            )
            Explainer("给这个时段起个名字，如「深夜静谧」「通勤提神」")

            // ── 时间段 ──
            Spacer(Modifier.height(14.dp))
            FieldLabel("时间段")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeBox(value = start, label = "开始") { pickingTime = "start" }
                Text("→", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                TimeBox(value = end, label = "结束") { pickingTime = "end" }
            }
            Explainer(
                if (end <= start) {
                    "结束早于开始：跨午夜，覆盖 ${TimeSlotConfig.formatMinutes(start)}–次日 ${TimeSlotConfig.formatMinutes(end)}（左闭右开）"
                } else {
                    "各时段之间不能有重合部分（左闭右开区间）"
                },
            )

            // ── 包含情绪词条 ──
            Spacer(Modifier.height(14.dp))
            FieldLabel("包含情绪词条")
            Spacer(Modifier.height(8.dp))
            // FlowRow 自动换行：词条数量随曲库增长，Row 不换行会横向溢出屏幕（2026-09-04 布局修复）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                availableTags.take(12).forEach { tag ->
                    MoodTagChip(
                        label = tag,
                        songCount = tagCounts[tag] ?: 0,
                        selected = tag in tags,
                        onClick = {
                            errorMessage = null
                            val willInclude = tag !in tags
                            tags = if (willInclude) tags + tag else tags - tag
                            if (willInclude) excludedTags = excludedTags - tag
                        },
                    )
                }
            }

            // ── 排除情绪词条 ──
            val excludedCandidates = availableTags.take(12).filterNot { it in tags }
            if (excludedCandidates.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FieldLabel("排除情绪词条")
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    excludedCandidates.forEach { tag ->
                        MoodTagChip(
                            label = tag,
                            songCount = tagCounts[tag] ?: 0,
                            selected = tag in excludedTags,
                            onClick = {
                                errorMessage = null
                                excludedTags = if (tag in excludedTags) {
                                    excludedTags - tag
                                } else {
                                    excludedTags + tag
                                }
                            },
                        )
                    }
                }
            }

            // ── 随机池合计 ──
            if (tags.isNotEmpty() || excludedTags.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "排除 ${poolStats.excluded} 首 · 情绪词条满足 ${poolStats.matched} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "合计可随机",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "${poolStats.available}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        " / $librarySize 首",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Explainer(
                    "包含 = 排除后仍命中包含词条，且词条在整首里的占比不低于" +
                        "${(MoodSlotPolicy.MATCH_MIN_RATIO * 100).toInt()}%；" +
                        "合计含未分析歌曲",
                )
                if (tags.isEmpty() && excludedTags.isNotEmpty()) {
                    Explainer("未选择包含词条：将在排除后的全库随机")
                }
            }

            // ── 错误信息 ──
            (conflict ?: localError)?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // ── 操作按钮 ──
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) { Text("取消") }
                androidx.compose.material3.Button(
                    onClick = {
                        val validation = resolver.validate(candidate, existingConfigs)
                        when (validation) {
                            is cn.com.dcsgo.mihx.domain.playback.SlotValidation.Valid -> onSave(candidate)
                            is cn.com.dcsgo.mihx.domain.playback.SlotValidation.Invalid ->
                                errorMessage = when (validation.error) {
                                    cn.com.dcsgo.mihx.domain.playback.SlotError.EMPTY_NAME -> "请填写时段名"
                                    cn.com.dcsgo.mihx.domain.playback.SlotError.NAME_TOO_LONG -> "时段名过长"
                                    cn.com.dcsgo.mihx.domain.playback.SlotError.NO_TAGS -> "请至少选择包含或排除词条"
                                    cn.com.dcsgo.mihx.domain.playback.SlotError.EXCLUDED_TAG_OVERLAP ->
                                        "排除词条不能与包含词条相同"
                                    cn.com.dcsgo.mihx.domain.playback.SlotError.ZERO_LENGTH -> "开始与结束时间不能相同"
                                }
                            is cn.com.dcsgo.mihx.domain.playback.SlotValidation.Conflict ->
                                errorMessage = "时间段与「${validation.conflicting.name}」重叠，请调整"
                        }
                    },
                    enabled = localError == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.height(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("保存时段")
                }
            }
        }
    }

    // ── 系统时间选择器 ──
    pickingTime?.let { which ->
        val initialMinutes = if (which == "start") start else end
        val timeState = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = true, // 设计文档 §2.4：存储统一分钟数，展示跟随系统；默认 24h 制
        )
        Dialog(onDismissRequest = { pickingTime = null }) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(22.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = timeState)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { pickingTime = null }) { Text("取消") }
                    TextButton(onClick = {
                        val minutes = timeState.hour * 60 + timeState.minute
                        if (which == "start") start = minutes else end = minutes
                        errorMessage = null
                        pickingTime = null
                    }) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Explainer(text: String, warn: Boolean = false) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        // 截图反馈（2026-09-04）：outline 色在深色主题过淡导致"文字看不清"，
        // 提升为 onSurfaceVariant；warn 走 tertiary 保持警示层级
        color = if (warn) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = MaterialTheme.typography.labelSmall.lineHeight * 1.35,
    )
}

@Composable
private fun TimeBox(value: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = TimeSlotConfig.formatMinutes(value),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}
