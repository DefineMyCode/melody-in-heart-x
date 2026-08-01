@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/** Reusable song list row. */
@Composable
fun SongRow(song: Song, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Column(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(text = song.title)
        Text(text = "${song.artist} · ${song.album}")
    }
}
