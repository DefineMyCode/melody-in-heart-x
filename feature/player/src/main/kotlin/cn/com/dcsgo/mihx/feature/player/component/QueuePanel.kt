@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.feature.player.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.ui.component.AlbumArtThumb

/**
 * Queue panel (plan P2-9): renders the full play-order [songs], highlights the item at
 * [currentIndex] (authoritative transport index, duplicates-safe), and supports tap-to-play,
 * remove-by-index and play-mode switch.
 */
@Composable
fun QueuePanel(
    songs: List<Song>,
    currentIndex: Int,
    playMode: PlayMode,
    onItemClick: (Int) -> Unit,
    onRemoveClick: (Int) -> Unit,
    onModeChange: (PlayMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
            Text(text = "播放队列 (${songs.size})", style = MaterialTheme.typography.titleMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PlayMode.entries.forEach { mode ->
                FilterChip(
                    selected = playMode == mode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(songs) { index, song ->
                val isCurrent = index == currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable { onItemClick(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtThumb(uri = song.albumArtUri, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title.ifBlank { "未知曲目" },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        if (song.artist.isNotBlank()) {
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    IconButton(onClick = { onRemoveClick(index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "移除")
                    }
                }
            }
        }
    }
}

private val PlayMode.label: String
    get() = when (this) {
        PlayMode.SEQUENTIAL -> "顺序"
        PlayMode.REVERSE -> "倒序"
        PlayMode.RANDOM -> "随机"
    }
