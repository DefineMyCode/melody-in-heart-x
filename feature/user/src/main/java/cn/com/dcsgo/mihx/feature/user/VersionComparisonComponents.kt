package cn.com.dcsgo.mihx.feature.user

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cn.com.dcsgo.mihx.core.common.time.formatDurationTime
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer.SpecCell

// ─────────────────────────────────────────────────────────────────
// 版本对比页（基准切换 + 差异表 + 折叠共同项 + A/B 对比聆听 + 删除）
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionComparisonScreen(
    songs: List<Song>,
    allSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onPlayVersion: (Song) -> Unit,
    onSeekTo: (Long) -> Unit,
    onDeleteSong: (Song) -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
) {
    // ── 各版本完整元数据（文件路径 + 比特率，与「查看歌曲详情」同源） ──
    var versionInfos by remember { mutableStateOf<Map<Int, SongInfo>>(emptyMap()) }
    LaunchedEffect(songs) {
        val loaded = buildMap {
            songs.forEach { song ->
                song.uri?.let { uri ->
                    loadSongInfo(song)?.let { info -> put(song.id, info) }
                }
            }
        }
        versionInfos = loaded
    }

    val comparison = remember(songs, versionInfos) {
        SongVersionComparer.compare(
            songs,
            bitRateOf = { song ->
                versionInfos[song.id]?.bitRate
                    ?.takeIf { it.isNotBlank() && it != "未知" }
                    .orEmpty()
            },
            fileSizeOf = { song ->
                versionInfos[song.id]?.fileSize
                    ?.takeIf { it.isNotBlank() && it != "未知" }
                    .orEmpty()
            },
        )
    }
    var referenceIndex by remember(comparison) { mutableStateOf(comparison.recommendedIndex) }
    var showCommon by remember { mutableStateOf(false) }

    // ── 删除确认 ──
    var songToDelete: Song? by remember { mutableStateOf(null) }
    songToDelete?.let { song ->
        DeleteVersionConfirmDialog(
            song = song,
            onDismiss = { songToDelete = null },
            onConfirm = {
                onDeleteSong(song)
                songToDelete = null
            },
        )
    }

    if (comparison.isEmpty) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "没有可对比的版本",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val referenceSong = comparison.versions[referenceIndex].song

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "版本对比",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${referenceSong.title} · ${referenceSong.artist} · ${comparison.versions.size} 版",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
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
        bottomBar = {
            ComparisonActionBar(
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                onSeekTo = onSeekTo,
                onPlayReference = { onPlayVersion(referenceSong) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            ReferenceStripSection(
                comparison = comparison,
                referenceIndex = referenceIndex,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onSelectReference = { referenceIndex = it },
                onDelete = { songToDelete = it },
            )

            DiffSummaryCard(
                comparison = comparison,
                referenceIndex = referenceIndex,
            )

            CommonInfoCard(
                commonFields = comparison.commonFields,
                expanded = showCommon,
                onToggle = { showCommon = !showCommon },
            )

            ComparisonTableCard(
                comparison = comparison,
                referenceIndex = referenceIndex,
                onSelectReference = { referenceIndex = it },
            )

            FilePathCard(
                versions = comparison.versions,
                songInfos = versionInfos,
                referenceIndex = referenceIndex,
                onSelectReference = { referenceIndex = it },
                onDelete = { songToDelete = it },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 底部操作条
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonActionBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    onPlayReference: () -> Unit,
) {
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val maxDuration = durationMs.coerceAtLeast(1L).toFloat()
    val shownPosition = (dragPosition ?: currentPositionMs.toFloat()).coerceIn(0f, maxDuration)

    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            if (durationMs > 0L) {
                Slider(
                    value = shownPosition,
                    onValueChange = { dragPosition = it },
                    onValueChangeFinished = {
                        dragPosition?.let { onSeekTo(it.toLong()) }
                        dragPosition = null
                    },
                    valueRange = 0f..maxDuration,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatDurationTime(shownPosition.toLong()),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatDurationTime(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPlayReference) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("播放选中")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 版本横滑条（基准选择 + 封面右下角删除入口）
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ReferenceStripSection(
    comparison: SongVersionComparer.ComparisonResult,
    referenceIndex: Int,
    currentSong: Song?,
    isPlaying: Boolean,
    onSelectReference: (Int) -> Unit,
    onDelete: (Song) -> Unit,
) {
    Column {
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
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
        border = androidx.compose.foundation.BorderStroke(
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
private fun RecommendBadge(modifier: Modifier = Modifier) {
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

// ─────────────────────────────────────────────────────────────────
// 差异总览
// ─────────────────────────────────────────────────────────────────

@Composable
private fun DiffSummaryCard(
    comparison: SongVersionComparer.ComparisonResult,
    referenceIndex: Int,
) {
    val reference = comparison.versions[referenceIndex]
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(84.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${comparison.versions.size}",
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "个版本",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SummaryTag(text = "共同 ${comparison.sameCount} 项", isDiff = false)
                    SummaryTag(text = "差异 ${comparison.diffCount} 项", isDiff = true)
                }
            }
        }
    }
}

@Composable
private fun SummaryTag(text: String, isDiff: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (isDiff) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (isDiff) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

// ─────────────────────────────────────────────────────────────────
// 共同信息（折叠）
// ─────────────────────────────────────────────────────────────────

@Composable
private fun CommonInfoCard(
    commonFields: List<Pair<String, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    if (commonFields.isEmpty()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "各版本一致",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${commonFields.size} 项",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    commonFields.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.width(64.dp),
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 规格对比表（首列固定 + 横滑 + 差异高亮 + 音质条）
// ─────────────────────────────────────────────────────────────────

@Composable
private fun ComparisonTableCard(
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
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
private fun LegendDot(text: String, isDiff: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (isDiff) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
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
private fun FormatBadge(
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
// 文件路径对比
// ─────────────────────────────────────────────────────────────────

@Composable
private fun FilePathCard(
    versions: List<SongVersionComparer.VersionCard>,
    songInfos: Map<Int, SongInfo>,
    referenceIndex: Int,
    onSelectReference: (Int) -> Unit,
    onDelete: (Song) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "文件路径",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            versions.forEachIndexed { index, card ->
                val displayPath = songInfos[card.song.id]?.filePath
                    ?.takeIf { it.isNotBlank() }
                    ?: "路径加载中…"
                val isReference = index == referenceIndex
                val rowBg = if (isReference) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isReference) {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        )
                        .background(rowBg)
                        .clickable { onSelectReference(index) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        VersionCover(song = card.song, size = 38.dp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FormatBadge(format = card.format, isLossless = card.isLossless, small = true)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = card.song.sampleRateDisplay,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isReference) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (card.isRecommended) {
                                Spacer(modifier = Modifier.width(6.dp))
                                RecommendBadge()
                            }
                            if (isReference) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "选中",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayPath,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isReference) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 删除本地文件
                    IconButton(
                        onClick = { onDelete(card.song) },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除此版本",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (index != versions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 封面
// ─────────────────────────────────────────────────────────────────

@Composable
private fun VersionCover(song: Song, size: androidx.compose.ui.unit.Dp) {
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
