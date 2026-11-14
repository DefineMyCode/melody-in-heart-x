package cn.com.dcsgo.mihx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Song

/**
 * 歌曲音质 + 空间音频徽章（列表行副信息）。
 *
 * - 音质档位：标准/较高/极高/无损/高解析；数据不足时不显示
 * - 空间音频：只有识别为支持时才显示图标，不支持不占位
 *
 * 行高只增加约 16dp，无数据时完全不占空间。
 */
@Composable
fun AudioSpecBadges(
    song: Song,
    modifier: Modifier = Modifier,
) {
    val quality = song.qualityLabel
    if (quality.isEmpty() && !song.spatialAudio) return

    Row(
        modifier = modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (quality.isNotEmpty()) {
            Text(
                text = quality,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        if (song.spatialAudio) {
            SpatialAudioIcon(modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * 空间音频图标（仅在支持时展示）。
 *
 * 图标来自 material-icons-extended，统一收在 core:ui，
 * 避免 feature 模块各自引入图标依赖。
 */
@Composable
fun SpatialAudioIcon(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = Icons.Default.SpatialAudio,
        contentDescription = "空间音频",
        modifier = modifier,
        tint = tint,
    )
}
