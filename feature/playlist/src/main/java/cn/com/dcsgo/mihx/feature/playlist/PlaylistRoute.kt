package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

data class PlaylistRouteState(
    val playlists: List<Playlist>,
    val librarySongs: List<Song>,
    val selectedPlaylist: Playlist?,
    val selectedPlaylistSongs: List<Song>?,
    val currentSong: Song?,
    val isPlaying: Boolean,
)

data class PlaylistRouteActions(
    val onPlaylistClick: (Playlist) -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onBackClick: () -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onDeletePlaylist: (Playlist) -> Unit,
    val onRenamePlaylist: (Playlist, String) -> Unit,
    val onAddSongToPlaylist: (Song, Playlist) -> Unit,
    val onRemoveSongFromPlaylist: (Song, Playlist) -> Unit,
    val onPlayAllInPlaylist: (List<Song>) -> Unit,
    val onPlayAllFromEndInPlaylist: (List<Song>) -> Unit,
    val onAddAllToQueueInPlaylist: (List<Song>) -> Int,
    val onAddAllToNextPlayInPlaylist: (List<Song>) -> Int,
    val onAddSongToQueue: (Song) -> Boolean,
    val onAddSongToNextPlay: (Song) -> Unit,
)

@Composable
fun PlaylistRoute(
    state: PlaylistRouteState,
    actions: PlaylistRouteActions,
    showToast: (String) -> Unit,
) {
    val visibleSongs = state.selectedPlaylistSongs ?: state.librarySongs

    PlaylistScreen(
        playlists = state.playlists,
        songs = visibleSongs,
        selectedPlaylist = state.selectedPlaylist,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        onPlaylistClick = actions.onPlaylistClick,
        onSongClick = { song -> actions.onSongClick(song, visibleSongs) },
        onBackClick = actions.onBackClick,
        onCreatePlaylist = actions.onCreatePlaylist,
        onDeletePlaylist = actions.onDeletePlaylist,
        onRenamePlaylist = actions.onRenamePlaylist,
        onAddSongToPlaylist = { song, playlist ->
            actions.onAddSongToPlaylist(song, playlist)
            showToast("已添加到「${playlist.name}」")
        },
        onRemoveSongFromPlaylist = { song, playlist ->
            actions.onRemoveSongFromPlaylist(song, playlist)
            showToast("已从「${playlist.name}」移除")
        },
        onPlayAllInPlaylist = { _, playlistSongs ->
            actions.onPlayAllInPlaylist(playlistSongs)
            showToast("已将 ${playlistSongs.size} 首歌曲加入播放队列")
        },
        onPlayAllFromEndInPlaylist = { _, playlistSongs ->
            actions.onPlayAllFromEndInPlaylist(playlistSongs)
            showToast("已将 ${playlistSongs.size} 首歌曲加入播放队列（从最后开始）")
        },
        onAddAllToQueueInPlaylist = { _, playlistSongs ->
            val added = actions.onAddAllToQueueInPlaylist(playlistSongs)
            showToast("已将 $added 首歌曲加入播放队列")
        },
        onAddAllToNextPlayInPlaylist = { _, playlistSongs ->
            val added = actions.onAddAllToNextPlayInPlaylist(playlistSongs)
            showToast("已将 $added 首歌曲设为下一首播放")
        },
        onAddSongToQueue = { song ->
            val added = actions.onAddSongToQueue(song)
            showToast(
                if (added) "已将「${song.title}」加入播放队列"
                else "「${song.title}」已在播放队列中",
            )
        },
        onAddSongToNextPlay = { song ->
            actions.onAddSongToNextPlay(song)
            showToast("已将「${song.title}」设为下一首播放")
        },
    )
}
