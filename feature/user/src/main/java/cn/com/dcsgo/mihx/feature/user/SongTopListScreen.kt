package cn.com.dcsgo.mihx.feature.user

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/** 榜单时间段 */
private enum class TopPeriod(val label: String) {
    WEEKLY("本周"),
    MONTHLY("本月"),
}

/**
 * 歌曲 TOP 榜全量页：周 / 月分段切换，按播放次数排序。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongTopListScreen(
    weeklyTop: List<Pair<Int, Int>>,
    monthlyTop: List<Pair<Int, Int>>,
    songs: List<Song>,
    currentSong: Song?,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
) {
    var period by remember { mutableStateOf(TopPeriod.WEEKLY) }
    val songsById = remember(songs) { songs.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌曲 TOP 榜") },
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
                .padding(padding),
        ) {
            // 周 / 月分段切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
            ) {
                TopPeriod.entries.forEach { item ->
                    val selected = period == item
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                            )
                            .clickable { period = item }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            val top = if (period == TopPeriod.WEEKLY) weeklyTop else monthlyTop
            val emptyText = if (period == TopPeriod.WEEKLY) {
                "本周还没有播放记录"
            } else {
                "本月还没有播放记录"
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (top.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        itemsIndexed(
                            top,
                            key = { _, pair -> pair.first },
                        ) { index, (songId, count) ->
                            val song = songsById[songId]
                            if (song != null) {
                                TopListRow(
                                    rank = index + 1,
                                    song = song,
                                    playCount = count,
                                    isPlaying = song.id == currentSong?.id,
                                    onClick = { onSongClick(song) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
