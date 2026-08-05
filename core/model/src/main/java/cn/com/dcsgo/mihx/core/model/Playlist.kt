package cn.com.dcsgo.mihx.core.model

import androidx.compose.runtime.Immutable

/**
 * 歌单数据模型。
 * [songIds] 为不可变 [List]，增删改通过 `copy(songIds = ...)` 生成新实例，
 * 使 Compose 能按值比较跳过重组（[Immutable]）。
 */
@Immutable
data class Playlist(
    val id: Int,
    val name: String,
    val songCount: Int,
    val songIds: List<Int> = emptyList(),
    val coverArt: Int = android.R.drawable.ic_media_play
)
