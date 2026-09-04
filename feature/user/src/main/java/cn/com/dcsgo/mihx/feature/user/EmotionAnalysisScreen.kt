package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.ui.components.EmotionCalibrateDialog

/**
 * 歌曲情绪分析"进度页": 批扫进度/耗时统计 + 暂停/继续.
 * 歌曲列表(搜索/词条过滤/多选/播放/信息)已搬去曲库「情绪」Tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmotionAnalysisScreen(
    state: EmotionAnalysisState,
    actions: EmotionAnalysisActions,
) {
    val scheme = MaterialTheme.colorScheme
    // 失败歌曲的弹层状态（2026-09-04）：手动标记 / 批量加入歌单
    var calibratingSong by remember { mutableStateOf<FailedEmotionSong?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌曲情绪分析") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProgressCard(state = state, onTogglePause = actions.onTogglePause)

            // ── 无法分析分区（2026-09-04）：失败歌曲 + 原因 + 手动标记/加歌单/重试 ──
            if (state.failures.isNotEmpty()) {
                Text(
                    text = "无法分析（${state.failures.size} 首）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = "以下歌曲已跳过分析并停止自动重试；修复问题后可手动重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.failures.forEach { failed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = failed.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = failed.reason.userMessage +
                                    if (failed.attempts > 1) "（失败 ${failed.attempts} 次）" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 手动标记情绪（校准弹窗）；失败歌无曲线，弹窗走词条-only 模式
                        TextButton(onClick = { calibratingSong = failed }) {
                            Text("标记")
                        }
                    }
                }
                Button(
                    onClick = actions.onRetryFailed,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("重试失败歌曲")
                }
            }

            // ── 手动标记情绪弹窗（失败歌曲无曲线，originals 传空、"恢复自动"不可见） ──
            calibratingSong?.let { failed ->
                EmotionCalibrateDialog(
                    initial = emptySet(),
                    originals = emptyList(),
                    hasUserTags = false,
                    onDismiss = { calibratingSong = null },
                    onConfirm = { words ->
                        actions.onCalibrateSong(failed.songId, words)
                        calibratingSong = null
                    },
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProgressCard(
    state: EmotionAnalysisState,
    onTogglePause: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val fraction = if (state.totalCount > 0) {
                state.analyzedCount.toFloat() / state.totalCount
            } else 0f
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 进度说明
            val pending = (state.totalCount - state.analyzedCount).coerceAtLeast(0)
            Text(
                text = when {
                    state.scanning -> "正在分析 ${state.analyzedCount}/${state.totalCount} 首" +
                        "（待分析 $pending）"
                    state.paused -> "已暂停：待分析 $pending 首"
                    state.totalCount == 0 -> "曲库还没有歌曲，导入后再分析情绪"
                    pending > 0 -> "已分析 ${state.analyzedCount}/${state.totalCount} 首，待分析 $pending 首"
                    else -> "全部 ${state.totalCount} 首分析完成"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (state.scanning && state.currentSongTitle != null) {
                Text(
                    text = "正在分析：${state.currentSongTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val stats = buildString {
                if (state.lastSongMs > 0) {
                    append("上一首耗时 ${fmtSec(state.lastSongMs)}")
                }
                if (state.avgSongMs > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("平均 ${fmtSec(state.avgSongMs)}/首")
                }
            }
            if (stats.isNotEmpty()) {
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "已分析 ${state.analyzedCount} · 手动标记 ${state.correctedCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 暂停/继续: 统计下面单开一行(文件扫描「开始校验」同款样式)
            if (state.scanning || state.paused) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onTogglePause,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.scanning) "暂停分析" else "继续分析")
                }
            }
        }
    }
}

private fun fmtSec(ms: Long): String =
    if (ms < 1000) "${ms}ms" else "%.1fs".format(ms / 1000.0)
