package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
    globalUniformRandomEnabled: Boolean,
    onGlobalUniformRandomEnabledChange: (Boolean) -> Unit,
    dailyListeningGoalMinutes: Int,
    onDailyListeningGoalMinutesChange: (Int) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SettingsThemeSelector(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    themeVariant = themeVariant,
                    onThemeVariantChange = onThemeVariantChange,
                )
                SettingsSwitchRow(
                    title = "全局均匀随机",
                    description = "随机时优先选择原始播放次数较少的歌曲",
                    checked = globalUniformRandomEnabled,
                    onCheckedChange = onGlobalUniformRandomEnabledChange,
                    switchContentDescription = "全局均匀随机开关",
                )
                SettingsDailyGoalSelector(
                    goalMinutes = dailyListeningGoalMinutes,
                    onGoalChange = onDailyListeningGoalMinutesChange,
                )
                SettingsActionButton(
                    title = "蓝牙播放监听",
                    description = "连接蓝牙耳机或车载音频时，断开连接会自动暂停播放",
                    actionText = "申请蓝牙权限",
                    onClick = onRequestBluetoothPermission,
                    actionContentDescription = "申请蓝牙权限",
                )
                SettingsActionButton(
                    title = "播放通知控制",
                    description = "在通知栏显示后台播放控制",
                    actionText = "申请通知权限",
                    onClick = onRequestNotificationPermission,
                    actionContentDescription = "申请通知权限",
                )
            }
        }
    }
}

@Composable
private fun SettingsThemeSelector(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    themeVariant: ThemeVariant,
    onThemeVariantChange: (ThemeVariant) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "主题",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeChip(
                    mode = mode,
                    selected = mode == themeMode,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeModeChange(mode) },
                )
            }
        }
        Text(
            text = "主题色",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 18.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeVariant.entries.forEach { variant ->
                ThemeVariantCard(
                    variant = variant,
                    selected = variant == themeVariant,
                    modifier = Modifier.weight(1f),
                    onClick = { onThemeVariantChange(variant) },
                )
            }
        }
    }
}

@Composable
private fun ThemeVariantCard(
    variant: ThemeVariant,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val swatchColors = when (variant) {
        ThemeVariant.MONO -> listOf(
            Color(0xFF000000),
            Color(0xFFD0D0D0),
            Color(0xFFF4F4F4),
        )
        ThemeVariant.VERMILION -> listOf(
            Color(0xFF000000),
            Color(0xFFC04F42),
            Color(0xFFFAF5F2),
        )
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            swatchColors.forEach { color ->
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(color),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = variant.label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(16.dp),
            )
        }
    }
}

@Composable
private fun ThemeModeChip(
    mode: ThemeMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = mode.label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchContentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics {
                contentDescription = switchContentDescription
            },
        )
    }
}

@Composable
private fun SettingsActionButton(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
    actionContentDescription: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.semantics {
                contentDescription = actionContentDescription
            },
        ) {
            Text(text = actionText)
        }
    }
}

private data class DailyGoalPreset(
    val minutes: Int,
    val label: String,
)

private val dailyGoalPresets = listOf(
    DailyGoalPreset(30, "30分钟"),
    DailyGoalPreset(60, "60分钟"),
    DailyGoalPreset(90, "90分钟"),
    DailyGoalPreset(120, "120分钟"),
)

/** 最大可自定义分钟数：24 小时 */
private const val MAX_GOAL_MINUTES = 1440

@Composable
private fun SettingsDailyGoalSelector(
    goalMinutes: Int,
    onGoalChange: (Int) -> Unit,
) {
    val isNoGoal = goalMinutes == 0
    val isCustom = goalMinutes > 0 && dailyGoalPresets.none { it.minutes == goalMinutes }
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "每日听歌时长目标",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 预设快捷选项行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            dailyGoalPresets.forEach { preset ->
                GoalPresetChip(
                    label = preset.label,
                    selected = preset.minutes == goalMinutes,
                    modifier = Modifier.weight(1f),
                    onClick = { onGoalChange(preset.minutes) },
                )
            }
        }

        // 自定义 + 无目标行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GoalPresetChip(
                label = if (isCustom) "自定义 · ${goalMinutes}分钟" else "自定义",
                selected = isCustom,
                modifier = Modifier.weight(1.2f),
                onClick = { showDialog = true },
            )
            GoalPresetChip(
                label = "无目标",
                selected = isNoGoal,
                modifier = Modifier.weight(0.8f),
                onClick = { onGoalChange(0) },
            )
        }
    }

    // 自定义目标对话框
    if (showDialog) {
        CustomGoalDialog(
            initialMinutes = if (isCustom) goalMinutes else 60,
            onConfirm = { minutes ->
                onGoalChange(minutes)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun CustomGoalDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in 1..MAX_GOAL_MINUTES

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义听歌时长目标") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }
                    if (filtered.length <= MAX_GOAL_MINUTES.toString().length) {
                        text = filtered
                    }
                },
                label = { Text("分钟") },
                placeholder = { Text("1～$MAX_GOAL_MINUTES") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it) } },
                enabled = isValid,
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun GoalPresetChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
