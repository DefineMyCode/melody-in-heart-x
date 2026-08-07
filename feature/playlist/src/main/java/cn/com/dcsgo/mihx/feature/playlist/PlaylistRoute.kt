package cn.com.dcsgo.mihx.feature.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo

@Stable
data class PlaylistRouteState(
    val playlists: List<Playlist>,
    val librarySongs: List<Song>,
    val libraryArtists: List<ArtistEntry>,
    val libraryAlbums: List<AlbumEntry>,
    val selectedPlaylist: Playlist?,
    val selectedPlaylistSongs: List<Song>?,
    val currentSong: Song?,
    val isPlaying: Boolean,
    val resumeSong: Song? = null,
    val isImporting: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0,
)

data class PlaylistRouteActions(
    val onPlaylistClick: (Playlist) -> Unit,
    val onSongClick: (Song, List<Song>) -> Unit,
    val onLocalSongClick: (Song) -> Unit,
    val onArtistClick: (String) -> Unit = {},
    val onAlbumClick: (String) -> Unit = {},
    val onBackClick: () -> Unit,
    val onCreatePlaylist: (String) -> Unit,
    val onDeletePlaylist: (Playlist) -> Unit,
    val onRenamePlaylist: (Playlist, String) -> Unit,
    val onAddSongToPlaylist: (Song, Playlist) -> Unit,
    val onRemoveSongFromPlaylist: (Song, Playlist) -> Unit,
    val onReorderPlaylist: (Int, List<Int>) -> Unit = { _, _ -> },
    // 本地音乐管理
    val onAddFolderClick: () -> Unit,
    val onAddSongsToPlaylist: (List<Song>, Playlist) -> Int,
    val onDeleteSong: (Song) -> Unit,
    val onCreatePlaylistWithResult: (String) -> Playlist?,
    val onShowVersionManagement: () -> Unit,
    val onShowQuickSkipSongs: () -> Unit,
    val onPlayAllInPlaylist: (List<Song>) -> Unit,
    val onPlayAllFromEndInPlaylist: (List<Song>) -> Unit,
    val onAddAllToQueueInPlaylist: (List<Song>) -> Int,
    val onAddAllToNextPlayInPlaylist: (List<Song>) -> Int,
    val onAddSongToQueue: (Song) -> Boolean,
    val onAddSongToNextPlay: (Song) -> Unit,
    val onResumePlaylist: (Song, List<Song>) -> Unit = { _, _ -> },
    val onDismissResume: () -> Unit = {},
)

@Composable
fun PlaylistRoute(
    state: PlaylistRouteState,
    actions: PlaylistRouteActions,
    loadSongInfo: suspend (Song) -> SongInfo?,
    showToast: (String) -> Unit,
) {
    val visibleSongs = state.selectedPlaylistSongs ?: state.librarySongs

    PlaylistScreen(
        playlists = state.playlists,
        songs = visibleSongs,
        libraryArtists = state.libraryArtists,
        libraryAlbums = state.libraryAlbums,
        selectedPlaylist = state.selectedPlaylist,
        currentSong = state.currentSong,
        isPlaying = state.isPlaying,
        resumeSong = state.resumeSong,
        onPlaylistClick = actions.onPlaylistClick,
        onSongClick = { song -> actions.onSongClick(song, visibleSongs) },
        onLocalSongClick = actions.onLocalSongClick,
        onArtistClick = actions.onArtistClick,
        onAlbumClick = actions.onAlbumClick,
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
        onReorderPlaylist = actions.onReorderPlaylist,
        isImporting = state.isImporting,
        importProgress = state.importProgress,
        importTotal = state.importTotal,
        onAddFolderClick = actions.onAddFolderClick,
        onAddSongsToPlaylist = { songs, playlist ->
            val added = actions.onAddSongsToPlaylist(songs, playlist)
            showToast(
                if (added > 0) "已将 $added 首歌曲添加到「${playlist.name}」"
                else "这些歌曲已在「${playlist.name}」中",
            )
        },
        onDeleteSong = actions.onDeleteSong,
        onCreatePlaylistWithResult = actions.onCreatePlaylistWithResult,
        onShowVersionManagement = actions.onShowVersionManagement,
        onShowQuickSkipSongs = actions.onShowQuickSkipSongs,
        loadSongInfo = loadSongInfo,
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
        onResumePlaylist = actions.onResumePlaylist,
        onDismissResume = actions.onDismissResume,
    )
}
