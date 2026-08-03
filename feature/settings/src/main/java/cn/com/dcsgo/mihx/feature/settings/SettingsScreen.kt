package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    darkThemeEnabled: Boolean,
    onDarkThemeEnabledChange: (Boolean) -> Unit,
    globalUniformRandomEnabled: Boolean,
    onGlobalUniformRandomEnabledChange: (Boolean) -> Unit,
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                SettingsSwitchRow(
                    title = "深色主题",
                    description = "使用深色外观",
                    checked = darkThemeEnabled,
                    onCheckedChange = onDarkThemeEnabledChange,
                    switchContentDescription = "深色主题开关",
                )
                SettingsSwitchRow(
                    title = "全局均匀随机",
                    description = "随机时优先选择原始播放次数较少的歌曲",
                    checked = globalUniformRandomEnabled,
                    onCheckedChange = onGlobalUniformRandomEnabledChange,
                    switchContentDescription = "全局均匀随机开关",
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
