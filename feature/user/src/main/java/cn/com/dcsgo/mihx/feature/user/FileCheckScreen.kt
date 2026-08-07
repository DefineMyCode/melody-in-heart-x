package cn.com.dcsgo.mihx.feature.user

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

/**
 * 本地歌曲文件校验结果页。
 *
 * 校验在后台运行，不暂停播放；本页可在任意时刻进入查看进度或结果，
 * 直到用户「确认完成」清除结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCheckScreen(
    validationResult: LocalFileValidationResult?,
    isValidating: Boolean,
    onBack: () -> Unit,
    onRunValidation: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "文件校验",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                isValidating -> ValidatingContent()
                validationResult == null -> IdleContent(onRunValidation = onRunValidation)
                else -> ResultContent(
                    result = validationResult,
                    onRerun = onRunValidation,
                    onAcknowledge = onAcknowledge,
                )
            }
        }
    }
}

@Composable
private fun ValidatingContent() {
    Spacer(modifier = Modifier.height(48.dp))
    CircularProgressIndicator(
        modifier = Modifier.size(44.dp),
        strokeWidth = 3.dp,
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "正在后台校验本地歌曲文件…",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "校验在后台运行，不会暂停播放；可继续听歌或浏览其它页面，\n完成后回到本页即可查看结果。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun IdleContent(onRunValidation: () -> Unit) {
    Spacer(modifier = Modifier.height(40.dp))
    Box(
        modifier = Modifier
            .size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primaryContainer,
        )
    }
    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "校验本地歌曲文件",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "扫描每首歌曲对应的本地文件是否存在，并检查与数据库是否一致。\n文件已缺失（如被外部删除）的歌曲将从曲库与歌单中移除，\n同时清理播放统计、秒切、播放事件等关联数据。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(28.dp))
    Button(
        onClick = onRunValidation,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("开始校验")
    }
}

@Composable
private fun ResultContent(
    result: LocalFileValidationResult,
    onRerun: () -> Unit,
    onAcknowledge: () -> Unit,
) {
    Spacer(modifier = Modifier.height(32.dp))
    Box(
        modifier = Modifier
            .size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (result.hasMissingFiles) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (result.hasMissingFiles) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = if (result.hasMissingFiles) "校验完成，发现失效文件" else "校验完成，数据一致",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(20.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            ResultRow(label = "扫描歌曲", value = "${result.totalSongs} 首")
            ResultRow(label = "文件缺失", value = "${result.missingCount} 首", emphasized = result.hasMissingFiles)
            ResultRow(label = "歌单引用清理", value = "${result.removedPlaylistRefs} 处")
            if (result.hasMissingFiles) {
                ResultRow(label = "关联数据清理", value = "播放统计 / 秒切 / 播放事件")
            }
        }
    }

    Spacer(modifier = Modifier.height(28.dp))
    Button(
        onClick = onAcknowledge,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("确认完成")
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
        onClick = onRerun,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("重新校验")
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasized: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
