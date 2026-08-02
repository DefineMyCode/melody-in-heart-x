@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song
import coil.compose.AsyncImage

/**
 * Reusable song list row with an optional embedded-album-art thumbnail (plan 5B). When
 * [Song.albumArtUri] is null the image slot is simply empty, so callers need no special handling.
 */
@Composable
fun SongRow(song: Song, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = song.title)
            Text(text = "${song.artist} · ${song.album}")
        }
    }
}
