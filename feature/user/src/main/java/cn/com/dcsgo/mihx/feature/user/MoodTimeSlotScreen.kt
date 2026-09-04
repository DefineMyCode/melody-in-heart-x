package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig

/** 词条 chip：名称 + 歌曲数角标（选中 = 已加入该时段） */
@Composable
internal fun MoodTagChip(
    label: String,
    songCount: Int,
    selected: Boolean,
    dim: Boolean = false,
    onClick: () -> Unit = {},
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (dim) 0.4f else 1f)
    }
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(container, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = songCount.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )
    }
}

/** 单个时段配置卡（列表页） */
@Composable
private fun SlotCard(
    config: TimeSlotConfig,
    isActive: Boolean,
    totalSongs: Int,
    tagCounts: Map<String, Int>,
    warnSmallPool: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = config.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = config.timeRangeText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "编辑时段", modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除时段",
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // 删除确认对话框（对齐 PlaylistDialogs.DeletePlaylistDialog 惯例：error 色确认按钮）
        if (showDeleteConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("删除时段") },
                text = {
                    Text(
                        "确定要删除「${config.name}」吗？\n\n" +
                            "仅移除此时段配置，歌曲与情绪数据不受影响。",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                },
            )
        }

        Spacer(Modifier.height(10.dp))
        // 词条 chips：FlowRow 自动换行（词条多时 Row 不换行会横向溢出屏幕，2026-09-04）
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            config.tags.take(6).forEach { tag ->
                MoodTagChip(label = tag, songCount = tagCounts[tag] ?: 0, selected = true)
            }
            if (config.tags.size > 6) {
                MoodTagChip(label = "+${config.tags.size - 6}", songCount = 0, selected = false, dim = true)
            }
        }

        Spacer(Modifier.height(11.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "合计 $totalSongs 首可随机",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isActive -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Text(
                        text = "生效中",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                warnSmallPool -> Text(
                    text = "歌曲较少，将循环播放",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** 24h 时间轴：时段覆盖色块 + 当前时刻指示 */
@Composable
private fun DayTimeline(
    configs: List<TimeSlotConfig>,
    nowMinuteOfDay: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(7.dp)),
        ) {
            val widthPx = maxWidth
            configs.forEach { config ->
                val isNow = config.covers(nowMinuteOfDay)
                val barColor = if (isNow) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                }
                val startPx = widthPx * (config.startMinutes / 1440f)
                if (!config.crossesMidnight) {
                    val spanPx = widthPx * ((config.endMinutes - config.startMinutes) / 1440f)
                    Box(
                        modifier = Modifier
                            .offset(x = startPx)
                            .width(spanPx)
                            .height(26.dp)
                            .background(barColor, RoundedCornerShape(5.dp)),
                    )
                } else {
                    // 跨午夜拆两段：[start, 24:00) 与 [0, end)
                    val headPx = widthPx * ((1440 - config.startMinutes) / 1440f)
                    Box(
                        modifier = Modifier
                            .offset(x = startPx)
                            .width(headPx)
                            .height(26.dp)
                            .background(barColor, RoundedCornerShape(5.dp)),
                    )
                    if (config.endMinutes > 0) {
                        val tailPx = widthPx * (config.endMinutes / 1440f)
                        Box(
                            modifier = Modifier
                                .offset(x = 0.dp)
                                .width(tailPx)
                                .height(26.dp)
                                .background(barColor, RoundedCornerShape(5.dp)),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0", "6", "12", "18", "24").forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** 配置列表页（内部） */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MoodTimeSlotScreen(
    state: MoodTimeSlotRouteState,
    actions: MoodTimeSlotRouteActions,
    showToast: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("随心播放增强", fontWeight = FontWeight.Bold)
                        Text(
                            "MOOD TIME SLOT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            letterSpacing = 1.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        floatingActionButton = {
            // 弹层打开时隐藏 FAB：否则悬浮在弹层上遮挡底部操作按钮（2026-09-04 布局修复）
            if (!state.dialogVisible) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = actions.onAddSlot,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("添加时段", fontWeight = FontWeight.Bold)
                }
            }
        },
    ) { padding ->
        if (state.configs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "还没有时段配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "添加一个时段并选择情绪词条，\n让随心播放跟着一天的心情走~",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 90.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 全局开关卡
            item(key = "global_switch", contentType = "switch") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
                        .padding(15.dp),
                ) {
                    // 标题紧贴开关：中间留 10dp 间距、不加 weight 撑满（2026-09-04 截图反馈）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "启用增强",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = state.enabled,
                            onCheckedChange = actions.onToggleEnabled,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "开启后，随心播放与无限随机将优先从当前时段配置的情绪歌曲中随机；未命中任何时段时保持原有随机",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 24h 时间轴
            item(key = "day_timeline", contentType = "timeline") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
                        .padding(15.dp),
                ) {
                    DayTimeline(configs = state.configs, nowMinuteOfDay = state.nowMinuteOfDay)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "色块 = 时段覆盖区间（跨午夜拆两段渲染）；实色为当前命中。同一时刻至多命中一个时段。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            // 时段卡
            items(configs2Items(state), key = { it.config.id }) { item ->
                SlotCard(
                    config = item.config,
                    isActive = item.config.covers(state.nowMinuteOfDay),
                    totalSongs = item.totalSongs,
                    tagCounts = state.tagCounts,
                    warnSmallPool = item.totalSongs < MOOD_POOL_WARN_THRESHOLD,
                    onEdit = { actions.onEditSlot(item.config) },
                    onDelete = { actions.onDeleteSlot(item.config.id) },
                )
            }
        }
    }
}

private data class SlotItem(val config: TimeSlotConfig, val totalSongs: Int)

/** 每卡合计可随机数：单词条直接取计数；多词条求和（同一首歌多词条只计一次的差异忽略，预览性质） */
private fun configs2Items(state: MoodTimeSlotRouteState): List<SlotItem> =
    state.configs.map { config ->
        val total = if (config.tags.size == 1) {
            state.tagCounts[config.tags.first()] ?: 0
        } else {
            config.tags.sumOf { state.tagCounts[it] ?: 0 }
        }
        SlotItem(config, total)
    }

internal const val MOOD_POOL_WARN_THRESHOLD = 10
