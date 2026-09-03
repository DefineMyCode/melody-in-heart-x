package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

@Composable
fun UserScreen(
    onShowSettings: () -> Unit = {},
    todayDurationMs: Long = 0L,
    weekTotalMs: Long = 0L,
    onOpenPlaybackStats: () ->
Unit = {},
    validationResult: LocalFileValidationResult? = null,
    isValidating: Boolean = false,
    onOpenFileCheck: () -> Unit = {},
    emotionAnalyzedCount: Int = 0,
    emotionTotalCount: Int = 0,
    emotionScanning: Boolean = false,
    emotionPaused: Boolean = false,
    onEmotionScanNow: () -> Unit = {},
    onOpenEmotionAnalysis: () -> Unit = {},
    moodSlotConfigs: List<TimeSlotConfig> = emptyList(),
    moodSlotEnabled: Boolean = false,
    nowMinuteOfDay: Int = 0,
    onOpenMoodTimeSlot: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "user_info", contentType = "header") {
                UserInfoSection(onSettingsClick = onShowSettings)
            }

            item(key = "play_stats", contentType = "header") {
                PlayStatsSection(
                    todayDurationMs = todayDurationMs,
                    weekTotalMs = weekTotalMs,
                    onOpenPlaybackStats = onOpenPlaybackStats,
                )
            }

            // 情境化随心播放增强入口卡（第 5 卡，视觉对齐其他入口卡）
            item(key = "mood_time_slot", contentType = "header") {
                MoodTimeSlotSection(
                    configs = moodSlotConfigs,
                    enabled = moodSlotEnabled,
                    nowMinuteOfDay = nowMinuteOfDay,
                    onClick = onOpenMoodTimeSlot,
                )
            }

            item(key = "emotion_scan", contentType = "header") {
                EmotionScanSection(
                    analyzedCount = emotionAnalyzedCount,
                    totalCount = emotionTotalCount,
                    scanning = emotionScanning,
                    paused = emotionPaused,
                    onScanNow = onEmotionScanNow,
                    onOpenDetail = onOpenEmotionAnalysis,
                )
            }

            item(key = "file_check", contentType = "header") {
                FileCheckSection(
                    validationResult = validationResult,
                    isValidating = isValidating,
                    onOpenFileCheck = onOpenFileCheck
                )
            }
        }
    }
}

/**
 * 情境化随心播放增强入口卡（设计稿 00 屏）：
 * 状态行（生效中：时段名 + 区间；未配置：引导文案）+ 词条 chips 摘要 + 右箭头。
 */
@Composable
internal fun MoodTimeSlotSection(
    configs: List<TimeSlotConfig>,
    enabled: Boolean,
    nowMinuteOfDay: Int,
    onClick: () -> Unit,
) {
    val active = configs.firstOrNull { it.covers(nowMinuteOfDay) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "随心播放增强",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = when {
                !enabled && configs.isEmpty() -> "未配置，点击添加"
                active != null -> "「${active.name}」生效中 · ${active.timeRangeText}"
                configs.isNotEmpty() -> "${configs.size} 个时段已配置 · 未在时段内"
                else -> "已配置但开关未开启"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (enabled && active != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                active.tags.take(4).forEach { tag ->
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(999.dp),
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
