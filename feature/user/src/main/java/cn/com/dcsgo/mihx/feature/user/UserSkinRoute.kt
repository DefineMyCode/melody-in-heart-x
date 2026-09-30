package cn.com.dcsgo.mihx.feature.user

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
// ImportSkinResult 与本文件同包 (cn.com.dcsgo.mihx.feature.user), 无需 import
import kotlinx.coroutines.launch

/**
 * P5 独立页:用户自定义皮肤(Q3 = 另开一个入口)。
 *
 * 入口在「我的」页底部 [CustomSkinSection],点击进。
 *
 * ## 流程
 *  - 顶部:返回 + 标题「自定义皮肤」
 *  - 中部:
 *   - 状态卡:当前是否装了?装了的话显示皮肤名(id 摘要)
 *   - 「导入皮肤」按钮 (主行动): 调 SAF 文件选择器,读 JSON 文本,
 *     调 AppRoot 的 UiTuningAccess.onImportUserSkin（debug 才有真实实现）。
 *  - 「还原默认」按钮(装了才可见): 调 onRestoreDefaultSkin
 *  - 「最近一次结果」提示
 *
 * ## 失败 UX (Q2 = 完整列表)
 *  导入失败 → 弹 [AlertDialog] 列全部 issue(code + field + message);
 *  点了「知道了」关闭 dialog,留在本页。
 *
 * ## release
 *  - release 包的 UiTuningAccess.onImportUserSkin 恒为 NotHandled,
 *    所以页面是「空转」状态:可以打开但什么也干不了。
 *    这与"该功能不在生产包可用"的语义一致——调试专用。
 */
@Composable
fun UserSkinRoute(
    hasUserSkin: Boolean,
    currentSkinName: String?,
    currentSkinId: String?,
    onBack: () -> Unit,
    onImportUserSkin: suspend (String) -> ImportSkinResult,
    onRestoreDefaultSkin: suspend () -> Unit,
    onShowToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Dialog 失败列表:本页生命周期内有效;若用户返回会清空(remember)。
    var pendingIssues by remember {
        mutableStateOf<List<cn.com.dcsgo.mihx.core.skin.SkinIssue>?>(null)
    }

    // SAF 文件选择器(任意文件类型,我们的解析层决定 JSON 是否合法)
    // 在 @Composable 作用域里捕获 context,避开"Lambda 不能调 @Composable"的限制。
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pickJson = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                ctx.contentResolver.openInputStream(uri)?.bufferedReader()
                    ?.use { it.readText() }
            } catch (t: Throwable) {
                onShowToast("读取文件失败: ${t.message ?: "未知错误"}")
                return@launch
            }
            if (text.isNullOrBlank()) {
                onShowToast("文件为空")
                return@launch
            }
            when (val r = onImportUserSkin(text)) {
                is ImportSkinResult.Success -> {
                    onShowToast("已切换到「${r.name}」")
                }
                is ImportSkinResult.Failed -> {
                    pendingIssues = r.issues   // 弹 dialog 列 issue(Q2)
                }
                is ImportSkinResult.Error -> {
                    onShowToast("导入失败: ${r.message}")
                }
                ImportSkinResult.NotHandled -> {
                    onShowToast("该功能在当前构建不可用")
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // 顶部 bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "自定义皮肤",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 状态卡
            StatusCard(
                hasUserSkin = hasUserSkin,
                currentSkinName = currentSkinName,
                currentSkinId = currentSkinId,
            )

            // 主行动:导入
            Button(
                onClick = { pickJson.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("导入皮肤 JSON")
            }
            Text(
                text = "从本地文件选择器挑一个 JSON 皮肤描述,导入后立即生效。\n" +
                    "皮肤描述的语法见仓库 docs/architecture/PLUGIN_SHELL_DESIGN.md。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 次行动:还原默认
            if (hasUserSkin) {
                OutlinedButton(
                    onClick = {
                        // suspend 回调:包进协程,不阻塞主线程(真机黑屏教训)
                        scope.launch {
                            onRestoreDefaultSkin()
                            onShowToast("已还原为默认皮肤")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("还原默认皮肤")
                }
            }
        }
    }

    // 失败 dialog:完整 issue 列表(Q2)
    pendingIssues?.let { issues ->
        AlertDialog(
            onDismissRequest = { pendingIssues = null },
            title = { Text("导入失败 (${issues.size} 条)") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    issues.forEachIndexed { idx, issue ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = issue.code.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = issue.field,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = issue.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        if (idx < issues.lastIndex) HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingIssues = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun StatusCard(
    hasUserSkin: Boolean,
    currentSkinName: String?,
    currentSkinId: String?,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (hasUserSkin) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (hasUserSkin) "已装用户皮肤" else "未装用户皮肤",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (hasUserSkin && currentSkinName != null) {
                    Text(
                        text = currentSkinName,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (hasUserSkin && currentSkinId != null) {
                    Text(
                        text = currentSkinId,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// 注: issues 列表仅在本页生命周期内有效;离开本页后状态清空,这是正确的(导入失败是
// "刚才那次"的事件,不该跨页持久化)。
