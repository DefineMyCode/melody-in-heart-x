package cn.com.dcsgo.mihx.data.local.migration

import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity

data class LegacyJsonSnapshot(
    val songs: List<SongEntity> = emptyList(),
    val songGroupOverrides: List<SongGroupOverrideEntity> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val playlistSongRefs: List<PlaylistSongCrossRef> = emptyList(),
    val playStats: List<PlayStatsEntity> = emptyList(),
    val quickSkipSongs: List<QuickSkipSongEntity> = emptyList(),
    val quickSkipShortPlayCounts: List<QuickSkipShortPlayEntity> = emptyList(),
)
