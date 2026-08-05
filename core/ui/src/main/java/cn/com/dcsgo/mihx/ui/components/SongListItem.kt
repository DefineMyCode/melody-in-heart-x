package cn.com.dcsgo.mihx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import cn.com.dcsgo.mihx.core.ui.R

/**
 * 歌曲条目「更多」菜单动作项（供各 feature 构造更多操作菜单复用）
 */
data class SongItemAction(
    val label: String,
    val onClick: () -> Unit,
    val destructive: Boolean = false,
    val leadingIcon: (@Composable () -> Unit)? = null,
)

/**
 * 共享歌曲列表行：封面 + 标题/歌手 + 播放中 EQ + 可选时长，头部/尾部用插槽自定义。
 *
 * 统一各 feature（歌单 / 秒切歌曲 / 播放队列）的歌曲行实现，避免重复代码。
 * 默认封面样式对齐设计 §5.13（44dp 缩略图、10dp 圆角、播放中高亮）。
 *
 * @param song              歌曲数据
 * @param isCurrentPlaying  是否正在播放（影响封面与标题样式，并显示 EQ 指示器）
 * @param contentPadding    行内边距（不同场景可覆盖）
 * @param showDuration      是否展示歌曲时长
 * @param onClick           整行点击回调
 * @param leading           行首插槽（多选指示 / 序号等）
 * @param cover             封面插槽，默认用 [DefaultSongCover]；参数为 isCurrentPlaying
 * @param trailing          行尾插槽（更多菜单 / 删除 / 移除等）
 */
@Composable
fun SongListItem(
    song: Song,
    isCurrentPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
    showDuration: Boolean = false,
    onClick: () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    cover: (@Composable (isCurrentPlaying: Boolean) -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        val coverContent: @Composable (Boolean) -> Unit = cover ?: { playing ->
            DefaultSongCover(song, playing)
        }
        coverContent(isCurrentPlaying)
        Spacer(modifier = Modifier.width(12.dp))
        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 播放中 EQ 动画指示器（内部强制 16dp，各变体视觉一致）
        if (isCurrentPlaying) {
            EqualizerIndicator(modifier = Modifier.width(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        // 歌曲时长（可选展示；等宽字体对齐设计 §3.1）
        if (showDuration && song.durationMs > 0L) {
            Text(
                text = formatDurationTime(song.durationMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        trailing?.invoke()
    }
}

/** 默认封面：44dp 缩略图（有专辑图显示图，否则播放/队列图标占位） */
@Composable
private fun DefaultSongCover(song: Song, isCurrentPlaying: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isCurrentPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (song.albumArtUri != null) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = "专辑封面",
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(
                    id = if (isCurrentPlaying) R.drawable.pause_24 else R.drawable.queue_music_24
                ),
                contentDescription = if (isCurrentPlaying) "正在播放" else "播放",
                modifier = Modifier.size(20.dp),
                tint = if (isCurrentPlaying) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.primary,
            )
        }
    }
}
