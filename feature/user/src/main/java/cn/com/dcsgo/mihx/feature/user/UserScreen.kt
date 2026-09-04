package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
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
 * 情境化随心播放增强入口卡（设计稿 00 屏）。
 *
 * 视觉与其他入口卡（播放统计/文件校验）严格对齐（2026-09-04 一致性修正）：
 * - Card 容器：bg1（surfaceContainerLowest）底 + out1 边框 + 14dp 圆角；
 * - 左侧 44dp 圆形图标容器（primaryContainer 底）+ 随心播放按钮同款 shuffle 图标；
 * - 右侧 KeyboardArrowRight 与卡片垂直居中（箭头随整卡 Row 居中，而非只对齐标题行）。
 */
@Composable
internal fun MoodTimeSlotSection(
    configs: List<TimeSlotConfig>,
    enabled: Boolean,
    nowMinuteOfDay: Int,
    onClick: () -> Unit,
) {
    val active = configs.firstOrNull { it.covers(nowMinuteOfDay) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 图标容器：44dp 圆形 primaryContainer 底 + shuffle 图标（随心播放按钮同款）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle_24),
                    contentDescription = "随心播放增强",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "随心播放增强",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        // 总开关关闭时功能整体未启用——即使恰好在某时段内也不算"生效中"
                        // （生效 = 开关开 + 时刻命中时段；与设计文档 §3.3 一致，2026-09-04 修正）
                        !enabled -> when {
                            configs.isEmpty() -> "未配置，点击添加"
                            else -> "功能未启用 · ${configs.size} 个时段已配置"
                        }
                        active != null -> "「${active.name}」生效中 · ${active.timeRangeText}"
                        else -> "${configs.size} 个时段已配置 · 未在时段内"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (enabled && active != null) {
                    Spacer(Modifier.height(6.dp))
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
            Spacer(Modifier.width(8.dp))
            // 箭头在整卡 Row 中垂直居中（其他卡同款）
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
