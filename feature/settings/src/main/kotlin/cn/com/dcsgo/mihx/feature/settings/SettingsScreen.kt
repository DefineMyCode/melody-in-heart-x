@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.SectionHeader
import cn.com.dcsgo.mihx.domain.repository.ThemeMode

/**
 * 设置 screen (plan P5-C4): player toggles (uniform-random / infinite play), connection toggles
 * (bluetooth / notification) and appearance (theme mode + dynamic color). Every control reads and
 * writes [SettingsFacade], which persists to Preferences DataStore via the domain repository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("设置") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item { SectionHeader("播放") }
            item {
                ToggleRow(
                    title = "均匀随机",
                    subtitle = "随机播放时优先选择播放次数较少的歌曲",
                    checked = state.uniformRandomEnabled,
                    onCheckedChange = viewModel::setUniformRandomEnabled,
                )
            }
            item {
                ToggleRow(
                    title = "无限播放",
                    subtitle = "接近队尾时自动补充未听过的歌曲",
                    checked = state.infinitePlayEnabled,
                    onCheckedChange = viewModel::setInfinitePlayEnabled,
                )
            }
            item { SectionHeader("连接") }
            item {
                ToggleRow(
                    title = "蓝牙控制",
                    subtitle = "蓝牙设备断开时自动暂停播放",
                    checked = state.bluetoothEnabled,
                    onCheckedChange = viewModel::setBluetoothEnabled,
                )
            }
            item {
                ToggleRow(
                    title = "通知栏控制",
                    subtitle = "在通知栏与锁屏显示媒体控件",
                    checked = state.notificationEnabled,
                    onCheckedChange = viewModel::setNotificationEnabled,
                )
            }
            item { SectionHeader("外观") }
            item {
                ListItem(
                    headlineContent = { Text("主题") },
                    supportingContent = {
                        Row(Modifier.padding(top = 4.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = state.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(themeLabel(mode)) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                    },
                )
            }
            item {
                ToggleRow(
                    title = "动态取色",
                    subtitle = "使用系统壁纸配色（Android 12+）",
                    checked = state.dynamicColorEnabled,
                    onCheckedChange = viewModel::setDynamicColorEnabled,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}
