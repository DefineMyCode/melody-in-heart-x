package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult

// ─────────────────────────────────────────────────────────────────
// 用户信息区域
// ─────────────────────────────────────────────────────────────────

/**
 * 用户页面顶部的用户信息展示区域
 * 显示应用图标、名称和简介
 */
@Composable
fun UserInfoSection(
    onSettingsClick: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(
                painter = painterResource(R.drawable.settings_24),
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            ) {
                Image(
                    painter = painterResource(R.drawable.dcsgo_logo),
                    contentDescription = "个人中心",
                    modifier = Modifier
                        .size(100.dp)
                        .align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_introduction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 播放统计区域
// ─────────────────────────────────────────────────────────────────

/**
 * 播放统计入口卡（单一入口）。
 *
 * 展示今日 / 本周时长的迷你预览，点击进入统计总览（今日、本周柱状图、周月榜单、
 * 全部歌曲统计均收纳在总览页内）。
 *
 * @param todayDurationMs   今日累计听歌时长（毫秒）
 * @param weekTotalMs       本周累计听歌时长（毫秒）
 * @param onOpenPlaybackStats 点击卡片进入统计总览
 */
@Composable
fun PlayStatsSection(
    todayDurationMs: Long = 0L,
    weekTotalMs: Long = 0L,
    onOpenPlaybackStats: () -> Unit = {},
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenPlaybackStats),
        // 对齐设计系统：卡片 bg1 底、14dp 圆角、out1 边框
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.bar_chart_4_bars_24),
                    contentDescription = "播放统计",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "播放统计",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "今日 ${formatHoursMinutes(todayDurationMs)} · 本周 ${formatHoursMinutes(weekTotalMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 文件校验区域
// ─────────────────────────────────────────────────────────────────

/**
 * 本地歌曲文件校验入口卡。
 *
 * 点击进入校验结果页；存在未确认的校验结果时展示「N 待确认」徽标。
 *
 * @param validationResult 上次校验结果（未确认前保留，null 表示尚无结果）
 * @param isValidating     校验是否正在后台运行
 * @param onOpenFileCheck  点击卡片进入校验页
 */
@Composable
fun FileCheckSection(
    validationResult: LocalFileValidationResult?,
    isValidating: Boolean,
    onOpenFileCheck: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFileCheck),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "文件校验",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "文件校验",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        isValidating -> "正在后台校验本地歌曲文件…"
                        validationResult != null && validationResult.missingCount > 0 ->
                            "发现 ${validationResult.missingCount} 首歌曲文件缺失，已清理关联数据"
                        validationResult != null -> "未发现失效文件，本地数据一致"
                        else -> "扫描并清理失效的本地歌曲文件"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (validationResult != null && validationResult.missingCount > 0) {
                Text(
                    text = "${validationResult.missingCount} 待确认",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
