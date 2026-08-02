package cn.com.dcsgo.mihx.feature.user

import cn.com.dcsgo.mihx.core.model.Song

/** One row of the playback-statistics list (title resolved from the library at build time). */
data class StatRowUi(
    val songId: Long,
    val title: String,
    val artist: String,
    val playCount: Long,
    val skipCount: Int,
    val shortPlayCount: Int,
)

/** One same-title group with its versions and the effective preferred id (user override or auto). */
data class SongGroupUi(
    val groupKey: String,
    val versions: List<Song>,
    val preferredSongId: Long?,
    val autoPreferredSongId: Long?,
)

/** UI state for the 我的 screen (plan P5-C5/C6). */
data class UserUiState(
    val isLoading: Boolean = true,
    val totalPlayedMs: Long = 0L,
    val topSongs: List<StatRowUi> = emptyList(),
    val skippedSongs: List<StatRowUi> = emptyList(),
    val versionGroups: List<SongGroupUi> = emptyList(),
)
