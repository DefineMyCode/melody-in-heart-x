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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer

// ─────────────────────────────────────────────────────────────────
// 版本横滑条：选择对比基准 + 封面角标 + 删除入口
// ─────────────────────────────────────────────────────────────────

@Composable
internal fun ReferenceStripSection(
    comparison: SongVersionComparer.ComparisonResult,
    referenceIndex: Int,
    currentSong: Song?,
    isPlaying: Boolean,
    onSelectReference: (Int) -> Unit,
    onDelete: (Song) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            itemsIndexed(
                items = comparison.versions,
                key = { _, card -> "vc_${card.song.id}" },
            ) { index, card ->
                VersionCardItem(
                    card = card,
                    isReference = index == referenceIndex,
                    isCurrentPlaying = isPlaying && currentSong?.id == card.song.id,
                    onClick = { onSelectReference(index) },
                    onDelete = { onDelete(card.song) },
                )
            }
        }
    }
}

@Composable
private fun VersionCardItem(
    card: SongVersionComparer.VersionCard,
    isReference: Boolean,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val borderColor = if (isReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val cardBg = if (isReference) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isReference) 2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Column(
            modifier = Modifier
                .width(124.dp)
                .padding(9.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                VersionCover(song = card.song, size = 90.dp)
                // 角标：推荐 / 选中 / 播放中
                if (card.isRecommended) {
                    RecommendBadge(modifier = Modifier.align(Alignment.TopStart))
                }
                if (isReference) {
                    SelectedBadge(modifier = Modifier.align(Alignment.TopStart))
                }
                if (isCurrentPlaying) {
                    Text(
                        text = "播放中",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(5.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(7.dp))
            // 采样率 · 编码格式 一行，末尾为删除入口（音质条上方）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.song.sampleRateDisplay.ifEmpty { card.format.ifEmpty { "未知" } },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(4.dp))
                FormatBadge(format = card.format, isLossless = card.isLossless, small = true)
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除此版本",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 音质条
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(card.qualityScore.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(
                            if (isReference) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun RecommendBadge(modifier: Modifier = Modifier) {
    Text(
        text = "推荐",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

@Composable
private fun SelectedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "已选",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
