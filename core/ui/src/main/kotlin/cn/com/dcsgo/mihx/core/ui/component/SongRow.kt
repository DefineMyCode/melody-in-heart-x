@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/**
 * Reusable song list row (UI 设计定稿统一版，合并 core 封面版与 Home 多选版):
 * - [selectable] prepends a [Checkbox] (home multi-select mode);
 * - [showCover] shows the album-art thumbnail via [AlbumArtThumb] (fallback: music-note icon);
 * - [selected] only matters when [selectable].
 */
@Composable
fun SongRow(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    selected: Boolean = false,
    selectable: Boolean = false,
    showCover: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(8.dp))
        }
        if (showCover) {
            AlbumArtThumb(uri = song.albumArtUri)
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title.ifBlank { "未知标题" })
            Text(
                text = if (showCover) {
                    "${song.artist.ifBlank { "未知艺人" }} · ${song.album.ifBlank { "未知专辑" }}"
                } else {
                    song.artist.ifBlank { "未知艺人" }
                },
            )
        }
    }
}
