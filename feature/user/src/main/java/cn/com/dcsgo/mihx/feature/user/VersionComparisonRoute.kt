package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo

@Stable
data class VersionComparisonRouteState(
    /** 当前分组下的所有版本 */
    val songs: List<Song>,
    /** 全库歌曲（备用上下文，未直接使用） */
    val allSongs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
    /** 当前播放位置（毫秒），用于底部进度条 */
    val currentPositionMs: Long,
    /** 当前播放歌曲时长（毫秒） */
    val durationMs: Long,
    /** 上一组/下一组的 groupKey；null = 已在开头/末尾 */
    val previousGroupKey: String? = null,
    val nextGroupKey: String? = null,
)

@Stable
data class VersionComparisonRouteActions(
    val onBack: () -> Unit,
    val onPlayVersion: (Song) -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onDeleteSong: (Song) -> Unit,
    /** 将某版本移出当前分组（独立成单曲） */
    val onDetachVersion: (Song) -> Unit,
    /** 将某版本关联到其他歌曲分组 */
    val onReassignVersion: (song: Song, targetSong: Song) -> Unit,
    /** 切换到相邻版本组（上一组/下一组） */
    val onNavigateGroup: (groupKey: String) -> Unit = {},
)

@Composable
fun VersionComparisonRoute(
    state: VersionComparisonRouteState,
    actions: VersionComparisonRouteActions,
    showToast: (String) -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
) {
    VersionComparisonScreen(
        songs = state.songs,
        allSongs = state.allSongs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        currentPositionMs = state.currentPositionMs,
        durationMs = state.durationMs,
        previousGroupKey = state.previousGroupKey,
        nextGroupKey = state.nextGroupKey,
        onBack = actions.onBack,
        onPlayVersion = { song ->
            actions.onPlayVersion(song)
            showToast("正在播放「${song.title}」(${song.sampleRateDisplay})")
        },
        onSeekTo = actions.onSeekTo,
        onDeleteSong = { song -> actions.onDeleteSong(song) },
        onDetachVersion = { song ->
            actions.onDetachVersion(song)
            showToast("已将「${song.title}」移出分组")
        },
        onReassignVersion = { song, targetSong ->
            actions.onReassignVersion(song, targetSong)
            showToast("已将「${song.title}」关联到「${targetSong.title}」分组")
        },
        onNavigateGroup = actions.onNavigateGroup,
        loadSongInfo = loadSongInfo,
    )
}
