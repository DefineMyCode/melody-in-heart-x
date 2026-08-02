@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.component.MelodyTopAppBar
import cn.com.dcsgo.mihx.core.ui.component.SectionHeader

/**
 * 我的 screen (plan P5-C5/C6): listening statistics (total time, top tracks, skips) and
 * same-title multi-version management. Tapping a version row pins it as the preferred one;
 * tapping it again drops the override so the highest sample rate wins automatically again.
 * The TopAppBar carries the 设置 entry (bottom navigation now only shows 曲库/播放/我的).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(viewModel: UserViewModel, onOpenSettings: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MelodyTopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = "累计收听",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatDuration(state.totalPlayedMs),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                }
            }
            if (state.topSongs.isNotEmpty()) {
                item { SectionHeader("Top 曲目") }
                // Key must be unique across the WHOLE LazyColumn, not just this list: the same song
                // can appear in both topSongs and skippedSongs, so the songId alone would collide
                // ("Key N was already used" crash). Prefix per list.
                items(state.topSongs, key = { "top_${it.songId}" }) { row ->
                    StatsRow(
                        title = row.title,
                        subtitle = "${row.playCount} 次播放",
                        artist = row.artist,
                    )
                }
            }
            if (state.skippedSongs.isNotEmpty()) {
                item { SectionHeader("秒切记录") }
                items(state.skippedSongs, key = { "skip_${it.songId}" }) { row ->
                    StatsRow(
                        title = row.title,
                        subtitle = "跳过 ${row.skipCount} 次 · 秒切 ${row.shortPlayCount} 次",
                        artist = row.artist,
                    )
                }
            }
            if (state.versionGroups.isNotEmpty()) {
                item { SectionHeader("同名多版本") }
                items(state.versionGroups, key = { it.groupKey }) { group ->
                    VersionGroupCard(group, viewModel::togglePreferredVersion)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(title: String, subtitle: String, artist: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(if (artist.isBlank()) subtitle else "$artist · $subtitle") },
    )
}

@Composable
private fun VersionGroupCard(group: SongGroupUi, onToggle: (String, Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            Text(
                text = group.groupKey,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            group.versions.forEach { song ->
                val effective = group.preferredSongId ?: group.autoPreferredSongId
                val selected = song.id == effective
                val autoRecommended = group.preferredSongId == null && song.id == group.autoPreferredSongId
                ListItem(
                    headlineContent = { Text(song.title) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(sampleRateLabel(song.sampleRate))
                                if (autoRecommended) append(" · 自动推荐")
                                song.titleOverride?.let { append(" · $it") }
                            },
                        )
                    },
                    leadingContent = {
                        RadioButton(selected = selected, onClick = { onToggle(group.groupKey, song.id) })
                    },
                    modifier = Modifier.clickable { onToggle(group.groupKey, song.id) },
                )
            }
        }
    }
}

private fun sampleRateLabel(rate: Int): String =
    if (rate > 0) "${rate / 1000.0} kHz" else "未知采样率"

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
}
