package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.common.format.formatFileSize
import cn.com.dcsgo.mihx.core.model.AudioQualityShare
import cn.com.dcsgo.mihx.core.model.AudioQualityTier
import cn.com.dcsgo.mihx.core.model.LocalMusicStorageStats
import cn.com.dcsgo.mihx.ui.components.SpatialAudioIcon
import java.util.Locale

/**
 * 存储与音质统计页。
 *
 * 入口在「本地音乐 → 文件管理」。
 * 音质档位统一由 [cn.com.dcsgo.mihx.core.model.Song.qualityTier] 判定；
 * 空间音频按 [AudioQualityTier.isSpatial] 的最新规则实时判定，避免旧库落库值影响结果。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageStatsScreen(
    stats: LocalMusicStorageStats?,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("存储与音质统计") },
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                stats == null && isLoading -> LoadingContent()
                stats == null || !stats.hasSongs -> EmptyContent()
                else -> StatsContent(stats)
            }
        }
    }
}

@Composable
private fun StatsContent(stats: LocalMusicStorageStats) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        StorageOverviewCard(stats)

        Spacer(modifier = Modifier.height(8.dp))
        QualityDistributionCard(stats)

        if (stats.unknownSizeSongs > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            UnknownSizeHintCard(stats)
        }

        Spacer(modifier = Modifier.height(8.dp))
        StatsScopeCard()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StorageOverviewCard(stats: LocalMusicStorageStats) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "总存储占用",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${stats.totalSongs} 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (stats.measuredSizeSongs > 0) formatFileSize(stats.totalBytes) else "—",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (stats.unknownSizeSongs == 0) {
                "已统计全部 ${stats.totalSongs} 首本地歌曲"
            } else {
                "已测量 ${stats.measuredSizeSongs} / ${stats.totalSongs} 首 · " +
                    "${stats.unknownSizeSongs} 首大小未知"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpatialAudioIcon(
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "空间音频",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stats.spatialAudioSongs} 首 · ${formatRatio(stats.spatialAudioRatio)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun QualityDistributionCard(stats: LocalMusicStorageStats) {
    StatsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "音质分布",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "按文件实际编码 / 位深 / 采样率 / 码率分级",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "共 ${stats.totalSongs} 首",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        QualityDistributionBar(shares = stats.qualityShares)

        Spacer(modifier = Modifier.height(6.dp))

        stats.qualityShares.forEachIndexed { index, share ->
            if (index > 0) {
                ThinDivider()
            }
            QualityShareRow(share)
        }
    }
}

@Composable
private fun UnknownSizeHintCard(stats: LocalMusicStorageStats) {
    StatsCard {
        Text(
            text = "${stats.unknownSizeSongs} 首歌曲未统计到文件大小",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "文件可能已被移动、删除或目录权限失效。可到「我的 → 文件校验」执行一次" +
                "深度校验，补齐库内文件指纹与大小后重新进入本页。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatsScopeCard() {
    StatsCard {
        Text(
            text = "统计口径",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        ScopeBullet("只统计通过「添加文件夹」导入、且本地文件仍存在的歌曲。")
        ScopeBullet("音质按文件实际参数判定，不参考平台会员档位；扩展名只是容器。")
        ScopeBullet("无损与高解析需要位深、采样率同时达标；DSD 直接归入高解析。")
        ScopeBullet("位深缺失但采样率达到 44.1kHz 时，仍按无损计。")
        ScopeBullet(
            "空间音频按最新规则实时判定：E-AC-3 JOC 直接计入；AC-4 仅大于 2 声道计入；" +
                "普通 E-AC-3 / 杜比数字+ 不计入。"
        )
        ScopeBullet("老数据执行一次深度校验后，列表图标与库内记录才会同步。")
        ScopeBullet("占用空间优先读取库内记录，缺失项再向系统查询实际文件大小。")
    }
}

@Composable
private fun ScopeBullet(text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QualityDistributionBar(shares: List<AudioQualityShare>) {
    val visibleShares = shares.filter { it.count > 0 }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val segmentColors = visibleShares.associate { share ->
        share.tier to qualityTierColor(share.tier)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(trackColor),
    ) {
        if (visibleShares.isEmpty()) return@Box

        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = 2.dp.toPx()
            val availableWidth = (
                size.width - gap * (visibleShares.size - 1).coerceAtLeast(0)
                ).coerceAtLeast(0f)
            var x = 0f
            visibleShares.forEachIndexed { index, share ->
                val segmentWidth = availableWidth * share.ratio
                if (segmentWidth > 0f) {
                    drawRect(
                        color = segmentColors[share.tier] ?: Color.Transparent,
                        topLeft = Offset(x, 0f),
                        size = Size(segmentWidth, size.height),
                    )
                }
                x += segmentWidth
                if (index != visibleShares.lastIndex) {
                    x += gap
                }
            }
        }
    }
}

@Composable
private fun QualityShareRow(share: AudioQualityShare) {
    val color = qualityTierColor(share.tier)
    val isEmpty = share.count == 0
    val emptyAlpha = if (isEmpty) 0.55f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isEmpty) color.copy(alpha = 0.25f) else color),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = share.tier.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = emptyAlpha),
            )
            Text(
                text = share.tier.rule,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = emptyAlpha),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${share.count} 首",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = emptyAlpha),
            )
            Text(
                text = formatRatio(share.ratio),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        // 对齐设计系统：卡片 bg1 底、14dp 圆角、out1 边框
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun ThinDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    )
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "正在统计文件大小…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.outlineVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "还没有本地音乐",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "导入歌曲后即可查看占用空间与音质分布",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 档位配色全部取自主题角色，不硬编码颜色，深浅色主题自动适配。
 */
@Composable
private fun qualityTierColor(tier: AudioQualityTier): Color {
    val scheme = MaterialTheme.colorScheme
    return when (tier) {
        AudioQualityTier.HI_RES -> scheme.primary
        AudioQualityTier.LOSSLESS -> scheme.primary.copy(alpha = 0.72f)
        AudioQualityTier.VERY_HIGH -> scheme.secondary
        AudioQualityTier.HIGH -> scheme.secondary.copy(alpha = 0.62f)
        AudioQualityTier.STANDARD -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
        AudioQualityTier.UNKNOWN -> scheme.outlineVariant.copy(alpha = 0.7f)
    }
}

private fun formatRatio(ratio: Float): String = when {
    ratio <= 0f -> "0%"
    ratio < 0.001f -> "<0.1%"
    else -> String.format(Locale.CHINA, "%.1f%%", ratio * 100f)
}