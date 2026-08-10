package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.domain.playback.SongVersionComparer

// ─────────────────────────────────────────────────────────────────
// 版本对比页容器：基准切换 + 差异总览 + 规格表 + 文件路径 + A/B 聆听
//
// 本文件只负责状态编排与区块拼装，具体区块分别位于：
// - [ComparisonActionBar]     → VersionComparisonActionBar.kt（播放控制）
// - [ReferenceStripSection]   → VersionReferenceStrip.kt（版本横滑条）
// - [DiffSummaryCard]/[CommonInfoCard] → VersionComparisonSummary.kt（差异总览）
// - [ComparisonTableCard]     → VersionComparisonTable.kt（规格差异表）
// - [FilePathCard]            → VersionFilePathCard.kt（文件路径）
// ─────────────────────────────────────────────────────────────────

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
                song.uri?.let {
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
