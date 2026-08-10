package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer.SpecCell
import coil.compose.AsyncImage

// ─────────────────────────────────────────────────────────────────
// 规格对比表：首列固定 + 横滑 + 差异高亮 + 音质条 + 封面/格式角标
//
// [FormatBadge] / [VersionCover] 为跨文件共享组件，故标 internal；
// 其余区块函数仅本表内部使用，保持 private。
// ─────────────────────────────────────────────────────────────────

/** 单元格相对基准的差异状态 */
private enum class CellState { BEST, DIFF, SAME }

private fun cellState(cells: List<SpecCell>, col: Int, referenceIndex: Int): CellState {
    if (col == referenceIndex) return CellState.BEST
    return if (cells[col].display == cells[referenceIndex].display) CellState.SAME else CellState.DIFF
}

private val tableHeaderHeight = 60.dp
private val tableRowHeight = 44.dp
private val tableColumnWidth = 84.dp
private val tableLabelWidth = 86.dp

@Composable
internal fun ComparisonTableCard(
    comparison: SongVersionComparer.ComparisonResult,
    referenceIndex: Int,
    onSelectReference: (Int) -> Unit,
) {
    if (comparison.rows.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "规格对比",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "左右滑动查看",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                // 固定首列（字段名）
                Column(modifier = Modifier.width(tableLabelWidth)) {
                    Spacer(modifier = Modifier.height(tableHeaderHeight))
                    comparison.rows.forEach { row ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(tableRowHeight),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = row.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }

                // 可横滑的版本列
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    comparison.versions.forEachIndexed { col, card ->
                        // 整列点击即可切换所选
                        Column(
                            modifier = Modifier
                                .width(tableColumnWidth)
                                .clickable { onSelectReference(col) },
                        ) {
                            // 列头（版本迷你卡）
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(tableHeaderHeight)
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(
                                        if (col == referenceIndex) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.4f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        VersionCover(song = card.song, size = 28.dp)
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = card.song.sampleRateDisplay.ifEmpty { card.format },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (col == referenceIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    if (col == referenceIndex) {
                                        Text(
                                            text = "选中",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            // 数据行
                            comparison.rows.forEach { row ->
                                val cell = row.cells[col]
                                val state = cellState(row.cells, col, referenceIndex)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(tableRowHeight)
                                        .background(
                                            if (col == referenceIndex) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            } else {
                                                Color.Transparent
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TableValueCell(
                                        cell = cell,
                                        state = state,
                                        isFormatRow = row.label == "格式",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableValueCell(
    cell: SpecCell,
    state: CellState,
    isFormatRow: Boolean,
) {
    if (isFormatRow) {
        FormatBadge(
            format = cell.display,
            isLossless = cell.isLossless ?: false,
            small = true,
        )
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val textColor = when (state) {
            CellState.BEST -> MaterialTheme.colorScheme.primary
            CellState.DIFF -> MaterialTheme.colorScheme.primary
            CellState.SAME -> MaterialTheme.colorScheme.outline
        }
        val bgColor = when (state) {
            CellState.DIFF -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }
        val fontWeight = when (state) {
            CellState.BEST, CellState.DIFF -> FontWeight.Bold
            CellState.SAME -> FontWeight.Normal
        }
        Text(
            text = cell.display,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = fontWeight,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
        val bar = cell.bar
        if (bar != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bar.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(
                            if (state == CellState.BEST) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun FormatBadge(
    format: String,
    isLossless: Boolean,
    small: Boolean = false,
) {
    Text(
        text = format.ifEmpty { "未知" },
        style = (if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
            .copy(fontWeight = FontWeight.Bold),
        color = if (isLossless) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isLossless) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = if (small) 5.dp else 7.dp, vertical = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────
// 封面（跨文件共享：横滑条 / 规格表 / 文件路径 复用）
// ─────────────────────────────────────────────────────────────────

@Composable
internal fun VersionCover(song: Song, size: Dp) {
    if (song.albumArtUri != null) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = "专辑封面",
            modifier = Modifier.size(size),
            contentScale = ContentScale.Crop,
        )
    } else {
        Icon(
            painter = painterResource(R.drawable.text_compare_24),
            contentDescription = null,
            modifier = Modifier.size(size * 0.5f),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
