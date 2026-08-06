package cn.com.dcsgo.mihx.feature.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo

@Stable
data class VersionManagementRouteState(
    val songs: List<Song>,
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class VersionManagementRouteActions(
    val onBack: () -> Unit,
    val onPlayVersion: (Song) -> Unit,
    val onAddToQueue: (Song) -> Boolean,
    val onDeleteSong: (Song) -> Unit,
    val onDetachVersion: (Song) -> Unit,
    val onReassignVersion: (song: Song, targetSong: Song) -> Unit,
    val onCompare: (SongVersionGroup) -> Unit = {},
    val onCopied: (String) -> Unit,
)

@Composable
fun VersionManagementRoute(
    state: VersionManagementRouteState,
    actions: VersionManagementRouteActions,
    showToast: (String) -> Unit,
    loadSongInfo: suspend (Song) -> SongInfo? = { null },
) {
    VersionManagementScreen(
        songs = state.songs,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onBack = actions.onBack,
        onPlayVersion = { song ->
            actions.onPlayVersion(song)
            showToast("正在播放「${song.title}」(${song.sampleRateDisplay})")
        },
        onAddToQueue = { song ->
            val added = actions.onAddToQueue(song)
            showToast(
                if (added) "已将「${song.title}」(${song.sampleRateDisplay}) 加入播放队列"
                else "「${song.title}」已在播放队列中",
            )
        },
        onDeleteSong = actions.onDeleteSong,
        onDetachVersion = { song ->
            actions.onDetachVersion(song)
            showToast("已将「${song.title}」移出分组")
        },
        onReassignVersion = { song, targetSong ->
            actions.onReassignVersion(song, targetSong)
            showToast("已将「${song.title}」关联到「${targetSong.title}」分组")
        },
        onCompare = actions.onCompare,
        onCopied = actions.onCopied,
        loadSongInfo = loadSongInfo,
    )
}
