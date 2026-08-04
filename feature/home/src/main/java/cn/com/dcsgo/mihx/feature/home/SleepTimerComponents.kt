package cn.com.dcsgo.mihx.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 自定义定时关闭时长的上限（分钟），下限固定为 1 分钟 */
private const val MAX_CUSTOM_MINUTES = 1440

/**
 * 定时关闭入口 Chip。
 *
 * 未设置时显示「定时关闭」；已设置时以倒计时形式展示剩余时间；
 * 「播完最后一曲」到点后展示「播完这首后关闭」。
 */
@Composable
fun SleepTimerChip(
    isActive: Boolean,
    remainingMs: Long,
    pausePending: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = {
            Text(
                text = when {
                    isActive && pausePending -> "播完这首后关闭"
                    isActive -> "关闭倒计时 ${formatRemaining(remainingMs)}"
                    else -> "定时关闭"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/**
 * 定时关闭设置弹窗：预设时长 + 自定义时长（不低于 1 分钟）+ 播完最后一曲开关。
 */
@Composable
fun SleepTimerDialog(
    isActive: Boolean,
    remainingMs: Long,
    playLastSong: Boolean,
    onDismiss: () -> Unit,
    onStart: (Int, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    val durationPresets = listOf(5, 15, 30, 45, 60, 90, 120)
    var selectedPreset by remember { mutableStateOf(30) }
    var useCustom by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf(30) }
    var customInput by remember { mutableStateOf("30") }
    var playLast by remember { mutableStateOf(playLastSong) }

    val selectedMinutes = if (useCustom) customMinutes else selectedPreset

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("定时关闭") },
        text = {
            Column {
                if (isActive) {
                    Text(
                        text = if (remainingMs <= 0L && playLastSong) {
                            "当前歌曲播完后暂停"
                        } else {
                            "剩余 ${formatRemaining(remainingMs)}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "多久后停止播放",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))

                durationPresets.chunked(4).forEach { rowPresets ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        rowPresets.forEach { minutes ->
                            FilterChip(
                                selected = !useCustom && selectedPreset == minutes,
                                onClick = {
                                    useCustom = false
                                    selectedPreset = minutes
                                },
                                label = {
                                    Text(
                                        text = durationLabel(minutes),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 3.dp),
                ) {
                    FilterChip(
                        selected = useCustom,
                        onClick = { useCustom = true },
                        label = {
                            Text(
                                text = "自定义",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }

                if (useCustom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = customInput,
                            onValueChange = { text ->
                                val digits = text.filter { it.isDigit() }.take(4)
                                val value = digits.toIntOrNull()
                                if (value != null) {
                                    val clamped = value.coerceIn(1, MAX_CUSTOM_MINUTES)
                                    customMinutes = clamped
                                    customInput = clamped.toString()
                                } else {
                                    customMinutes = 1
                                    customInput = digits
                                }
                            },
                            modifier = Modifier.width(96.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            label = { Text("分钟") },
                        )
                        IconButton(
                            onClick = { customMinutes = (customMinutes - 1).coerceAtLeast(1); customInput = customMinutes.toString() },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Remove,
                                contentDescription = "减少 1 分钟",
                            )
                        }
                        IconButton(
                            onClick = { customMinutes = (customMinutes + 1).coerceAtMost(MAX_CUSTOM_MINUTES); customInput = customMinutes.toString() },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "增加 1 分钟",
                            )
                        }
                    }
                    Text(
                        text = "自定义时长不能低于 1 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "播完最后一曲",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "到点后当前歌曲播放完毕再暂停",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = playLast, onCheckedChange = { playLast = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onStart(selectedMinutes.coerceAtLeast(1), playLast)
                    onDismiss()
                },
            ) {
                Text(if (isActive) "重新定时" else "开始定时")
            }
        },
        dismissButton = {
            Row {
                if (isActive) {
                    TextButton(
                        onClick = {
                            onCancel()
                            onDismiss()
                        },
                    ) {
                        Text("取消定时")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

/** 剩余时间格式：mm:ss 或 h:mm:ss */
fun formatRemaining(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun durationLabel(minutes: Int): String = when (minutes) {
    60 -> "1小时"
    90 -> "1.5小时"
    120 -> "2小时"
    else -> "${minutes}分"
}
