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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.core.model.WordPreferenceKind

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
    // 探索播放「重新探索」确认弹窗
    var showClearConfirm by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("随心播放增强", fontWeight = FontWeight.Bold)
                        Text(
                            "RANDOM ENHANCE",
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
            // 弹层打开或非时段模式时隐藏 FAB（探索/关闭模式不提供时段编辑入口）
            if (!state.dialogVisible && state.mode == RandomEnhanceMode.TIME_SLOT) {
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
        // 评审 2026-10-27 · M-15：时段卡的「合计可随机数」记忆化。
        // 原先这个派生直接写在列表的 content 里，每次重组都重算「时段数 × 全库」；
        // 现在只在配置或统计变化时算一次。
        // （注意：必须放在 LazyColumn 之外 —— LazyListScope 不是组合作用域。）
        val slotItems = remember(state.configs, state.tagCounts) {
            configs2Items(state)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 90.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 运行模式选择卡（时段 / 探索 互斥，始终可见）
            item(key = "mode_selector", contentType = "mode") {
                ModeSelectorCard(
                    mode = state.mode,
                    onSelectMode = actions.onSelectMode,
                )
            }

            if (state.mode == RandomEnhanceMode.EXPLORE) {
                // 探索模式说明卡
                item(key = "explore_info", contentType = "explore") {
                    ExploreInfoCard()
                }
                // 探索播放：当前喜欢/中立/不喜欢词条 + 重新探索
                item(key = "explore_word_prefs", contentType = "wordprefs") {
                    ExploreWordPreferenceCard(
                        preferences = state.wordPreferences,
                        onClear = { showClearConfirm = true },
                    )
                }
            } else if (state.mode == RandomEnhanceMode.TIME_SLOT) {
                if (state.configs.isEmpty()) {
                    // 时段列表空态（模式选择卡已在上方展示）
                    item(key = "empty_state", contentType = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
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
                    }
                } else {
                    // 全局开关提示卡（时段模式生效说明）
                    item(key = "global_hint", contentType = "hint") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
                                .padding(15.dp),
                        ) {
                            Text(
                                "时段模式生效中",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "随心播放与无限随机将优先从当前时段配置的情绪歌曲中随机；未命中任何时段时保持原有随机",
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
                    items(slotItems, key = { it.config.id }) { item ->
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

        // 探索播放「重新探索」确认弹窗
        if (showClearConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("重新探索？") },
                text = {
                    Text(
                        "将清空所有喜欢 / 不喜欢词条的学习统计，之后探索播放会重新开始学习。",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirm = false
                            actions.onClearWordPreferences()
                        },
                    ) {
                        Text("清空并重新开始")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("取消")
                    }
                },
            )
        }
    }
}

/** 运行模式选择卡：关闭 / 时段模式 / 探索模式，互斥单选 */
@Composable
private fun ModeSelectorCard(
    mode: RandomEnhanceMode,
    onSelectMode: (RandomEnhanceMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "运行模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (mode == RandomEnhanceMode.OFF) "未启用" else "已启用",
                style = MaterialTheme.typography.labelSmall,
                color = if (mode == RandomEnhanceMode.OFF) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeOptionChip(
                label = "关闭",
                selected = mode == RandomEnhanceMode.OFF,
                onClick = { onSelectMode(RandomEnhanceMode.OFF) },
            )
            ModeOptionChip(
                label = "时段模式",
                selected = mode == RandomEnhanceMode.TIME_SLOT,
                onClick = { onSelectMode(RandomEnhanceMode.TIME_SLOT) },
            )
            ModeOptionChip(
                label = "探索模式",
                selected = mode == RandomEnhanceMode.EXPLORE,
                onClick = { onSelectMode(RandomEnhanceMode.EXPLORE) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "时段模式与探索模式互斥（只能启用一个）。时段模式按时刻 × 情绪词条随机；" +
                "探索模式自动随机 10 首并按收听习惯学习喜欢/不喜欢词条。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
        },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** 探索模式说明卡 */
@Composable
private fun ExploreInfoCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Text(
            "探索模式",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "· 点击「随心播放」随机生成 10 首开始探索\n" +
                "· 无限随机播放被禁用（按钮显示「探索播放中」）\n" +
                "· 一首歌没听满 30 秒或 1/4 时长 → 情绪词条记入「不喜欢」；反之记入「喜欢」（计票制）\n" +
                "· 队列剩 1 首时自动补 5 首，优先含喜欢词条、尽量避开不喜欢词条的歌曲",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 探索播放：当前喜欢/中立/不喜欢词条 + 重新探索按钮 */
@Composable
private fun ExploreWordPreferenceCard(
    preferences: List<WordPreference>,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .padding(15.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "已学习的词条",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) {
                Text("重新探索", color = MaterialTheme.colorScheme.error)
            }
        }

        val liked = preferences.filter { it.kind == WordPreferenceKind.LIKE }
        val neutral = preferences.filter { it.kind == WordPreferenceKind.NEUTRAL }
        val disliked = preferences.filter { it.kind == WordPreferenceKind.DISLIKE }

        if (liked.isEmpty() && neutral.isEmpty() && disliked.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "还没有学习到词条：完整听一首歌 → 词条进「喜欢」；快速跳过 → 词条进「不喜欢」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PreferenceRow(
                label = "喜欢",
                items = liked,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            PreferenceRow(
                label = "中立",
                items = neutral,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            PreferenceRow(
                label = "不喜欢",
                items = disliked,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** 一组词条：标签 + 词条 chip（含「喜欢票:不喜欢票」角标） */
@Composable
private fun PreferenceRow(
    label: String,
    items: List<WordPreference>,
    container: Color,
    content: Color,
) {
    Column {
        Text(
            text = "$label（${items.size}）",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        if (items.isEmpty()) {
            Text(
                "暂无",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEach { pref ->
                    Row(
                        modifier = Modifier
                            .background(container, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = pref.word,
                            style = MaterialTheme.typography.labelMedium,
                            color = content,
                        )
                        Text(
                            // 显示占比而不是票数: 票是「占比 ×100」的加权票,
                            // 绝对值是 15~1500 这种量级, 对用户没意义;
                            // 占比才是判定依据(票多者胜)
                            text = when (pref.kind) {
                                cn.com.dcsgo.mihx.core.model.WordPreferenceKind.NEUTRAL -> "中立"
                                else -> "喜 ${pref.likeSharePercent}%"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

private data class SlotItem(val config: TimeSlotConfig, val totalSongs: Int)

/** 每卡合计可随机数：按与播放时一致的判定算（词条占比够高才算命中） */
private fun configs2Items(state: MoodTimeSlotRouteState): List<SlotItem> =
    state.configs.map { config ->
        SlotItem(config, state.poolCountOf(config.tags.toSet()))
    }

internal const val MOOD_POOL_WARN_THRESHOLD = 10
