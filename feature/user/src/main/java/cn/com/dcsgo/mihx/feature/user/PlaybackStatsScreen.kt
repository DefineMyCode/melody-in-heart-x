package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.common.time.formatHoursMinutes
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot

/**
 * 播放统计中心（总览页）。
 *
 * 结构：今日听歌时长（环形进度 + 增量）→ 本周柱状图 → 本周 / 本月 TOP 榜预览 → 全部歌曲统计入口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackStatsScreen(
    snapshot: PlaybackStatsSnapshot,
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onOpenPlayCounts: () -> Unit,
    onOpenEffectivePlayCounts: () -> Unit,
    onOpenWeeklyTop: () -> Unit,
    onOpenMonthlyTop: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("播放统计") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TodayCard(snapshot = snapshot, isPlaying = isPlaying, currentSong = currentSong)

            WeekChartCard(snapshot = snapshot)

            SectionHeader(title = "本周歌曲 TOP 榜", showMore = true, onMoreClick = onOpenWeeklyTop)
            TopPreview(
                top = snapshot.weeklyTop,
                songs = songs,
                currentSong = currentSong,
                emptyText = "本周还没有播放记录",
                onSongClick = onSongClick,
            )

            SectionHeader(title = "本月歌曲 TOP 榜", showMore = true, onMoreClick = onOpenMonthlyTop)
            TopPreview(
                top = snapshot.monthlyTop,
                songs = songs,
                currentSong = currentSong,
                emptyText = "本月还没有播放记录",
                onSongClick = onSongClick,
            )

            SectionHeader(title = "全部歌曲统计")
            StatsEntryRow(
                icon = R.drawable.bar_chart_4_bars_24,
                title = "播放次数",
                subtitle = "全部歌曲按播放次数排序 · 可搜索 / 排序",
                onClick = onOpenPlayCounts,
            )
            StatsEntryRow(
                icon = R.drawable.bar_chart_24,
                title = "有效播放次数",
                subtitle = "完播率达 90% 或超过 5 分钟才计一次",
                onClick = onOpenEffectivePlayCounts,
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// 今日听歌时长
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TodayCard(
    snapshot: PlaybackStatsSnapshot,
    isPlaying: Boolean,
    currentSong: Song?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.bar_chart_4_bars_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "今日听歌时长",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "目标 ${DAILY_GOAL_MINUTES / 60} 小时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TodayRing(
                    progress = snapshot.todayDurationMs / (DAILY_GOAL_MINUTES * 60_000f),
                    modifier = Modifier.size(84.dp),
                )
                Column {
                    Text(
                        text = formatHoursMinutes(snapshot.todayDurationMs),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val playingSuffix = if (isPlaying && currentSong != null) {
                        " · 正在播放「${currentSong.title}」"
                    } else {
                        ""
                    }
                    Text(
                        text = "今日已听 ${snapshot.todaySongCount} 首$playingSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeltaStat(
                    label = "较昨日",
                    diffMs = snapshot.todayDurationMs - snapshot.yesterdayDurationMs,
                    modifier = Modifier.weight(1f),
                )
                DeltaStat(
                    label = "较上周同日",
                    diffMs = snapshot.todayDurationMs - snapshot.lastWeekSameDayDurationMs,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DeltaStat(
    label: String,
    diffMs: Long,
    modifier: Modifier = Modifier,
) {
    val positive = diffMs > 0
    val text = when {
        diffMs > 0 -> "+${formatHoursMinutes(diffMs)}"
        diffMs < 0 -> "-${formatHoursMinutes(-diffMs)}"
        else -> "持平"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (positive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────
// 本周听歌时长 · 柱状图
// ─────────────────────────────────────────────────────────────────

@Composable
private fun WeekChartCard(snapshot: PlaybackStatsSnapshot) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.bar_chart_24),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.size(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本周听歌时长",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "合计 ${formatHoursMinutes(snapshot.weekTotalMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val percent = if (snapshot.lastWeekTotalMs > 0L) {
                    ((snapshot.weekTotalMs - snapshot.lastWeekTotalMs) * 100 / snapshot.lastWeekTotalMs).toInt()
                } else {
                    0
                }
                DeltaChip(
                    text = if (snapshot.lastWeekTotalMs > 0L) {
                        "${if (percent >= 0) "+" else ""}$percent%"
                    } else {
                        "较上周 —"
                    },
                    positive = percent >= 0,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            WeekBarChart(weekDays = snapshot.weekDays)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "柱体数值单位：分钟 · 无听歌记录的日期留空",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
// TOP 榜预览
// ─────────────────────────────────────────────────────────────────

@Composable
private fun TopPreview(
    top: List<Pair<Int, Int>>,
    songs: List<Song>,
    currentSong: Song?,
    emptyText: String,
    onSongClick: (Song) -> Unit,
) {
    val songsById = rememberSongsById(songs)
    val rows = top.mapNotNull { (songId, count) ->
        songsById[songId]?.let { song -> song to count }
    }
    if (rows.isEmpty()) {
        Text(
            text = emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        return
    }
    rows.take(3).forEachIndexed { index, (song, count) ->
        TopListRow(
            rank = index + 1,
            song = song,
            playCount = count,
            isPlaying = song.id == currentSong?.id,
            onClick = { onSongClick(song) },
        )
    }
}

@Composable
private fun rememberSongsById(songs: List<Song>): Map<Int, Song> {
    return remember(songs) { songs.associateBy { it.id } }
}
