package cn.com.dcsgo.mihx.app

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import cn.com.dcsgo.mihx.feature.home.PlayStatsRouteState
import cn.com.dcsgo.mihx.feature.player.PlayerUiState
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel
import cn.com.dcsgo.mihx.feature.playlist.PlaylistRouteState
import cn.com.dcsgo.mihx.feature.user.PlaybackStatsRouteState
import cn.com.dcsgo.mihx.feature.user.SongTopListRouteState
import cn.com.dcsgo.mihx.feature.user.UserRouteState

/**
 * 路由 State 派生映射
 *
 * 把「PlayerUiState + 统计快照 → 各 feature Route State」的纯业务派生逻辑集中在此，
 * 使 [AppNavHost] 只负责路由表与页面装配。
 * 本文件中的函数均为纯函数（无导航副作用），可独立测试。
 */

internal fun playlistRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    selectedPlaylist: Playlist?,
    resumeSong: Song? = null,
): PlaylistRouteState = PlaylistRouteState(
    playlists = uiState.playlists,
    librarySongs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
    libraryArtists = uiState.libraryArtists,
    libraryAlbums = uiState.libraryAlbums,
    selectedPlaylist = selectedPlaylist,
    resumeSong = resumeSong,
    selectedPlaylistSongs = selectedPlaylist?.let { playlist ->
        playerViewModel.getGroupedSongs(
            playerViewModel.getSongsByPlaylist(playlist),
        ).flatten()
    },
    currentSong = uiState.currentSong,
    isPlaying = uiState.isPlaying,
    isImporting = uiState.isImporting,
    importProgress = uiState.importProgress,
    importTotal = uiState.importTotal,
)

internal fun userRouteState(
    snapshot: PlaybackStatsSnapshot,
    validationResult: LocalFileValidationResult? = null,
    isValidating: Boolean = false,
): UserRouteState = UserRouteState(
    todayDurationMs = snapshot.todayDurationMs,
    weekTotalMs = snapshot.weekTotalMs,
    validationResult = validationResult,
    isValidating = isValidating,
)

internal fun playStatsRouteState(
    title: String,
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    rankedCounts: List<Pair<Int, Int>>,
): PlayStatsRouteState {
    val allSongs = playerViewModel.getGroupedSongs(uiState.songs).flatten()
    val statsContent = rankedStatsContent(
        songs = allSongs,
        rankedCounts = rankedCounts,
    )
    return PlayStatsRouteState(
        title = title,
        songs = statsContent.songs,
        playCounts = statsContent.playCounts,
        currentSong = uiState.currentSong,
    )
}

internal fun playbackStatsRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    snapshot: PlaybackStatsSnapshot,
): PlaybackStatsRouteState = PlaybackStatsRouteState(
    snapshot = snapshot,
    songs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
    currentSong = uiState.currentSong,
    isPlaying = uiState.isPlaying,
    dailyListeningGoalMinutes = uiState.dailyListeningGoalMinutes,
)

internal fun songTopListRouteState(
    uiState: PlayerUiState,
    playerViewModel: PlayerViewModel,
    snapshot: PlaybackStatsSnapshot,
    initialPeriod: String = "week",
): SongTopListRouteState = SongTopListRouteState(
    weeklyTop = snapshot.weeklyTop,
    monthlyTop = snapshot.monthlyTop,
    songs = playerViewModel.getGroupedSongs(uiState.songs).flatten(),
    currentSong = uiState.currentSong,
    initialPeriod = initialPeriod,
)

/** 解析续播记录对应的可播放歌曲：不在歌单里/文件不可播(uri==null)时返回 null */
fun resolveResumeSong(
    resume: PlaylistResume?,
    allSongs: List<Song>,
    playlistSongIds: Set<Int>,
    isPlayable: (Song) -> Boolean = { it.uri != null },
): Song? = resume?.let { r ->
    allSongs.firstOrNull { it.id == r.songId }
        ?.takeIf(isPlayable)
        ?.takeIf { it.id in playlistSongIds }
}

private data class RankedStatsContent(
    val songs: List<Song>,
    val playCounts: Map<Int, Int>,
)

/** 已播歌曲按排名在前、未播歌曲按原序在后，播放次数取排名结果。 */
private fun rankedStatsContent(
    songs: List<Song>,
    rankedCounts: List<Pair<Int, Int>>,
): RankedStatsContent {
    val rankedSongIds = rankedCounts.mapTo(mutableSetOf()) { it.first }
    val songsById = songs.associateBy { it.id }
    val rankedSongs = rankedCounts.mapNotNull { (songId, _) -> songsById[songId] }
    val unplayedSongs = songs.filterNot { it.id in rankedSongIds }

    return RankedStatsContent(
        songs = rankedSongs + unplayedSongs,
        playCounts = rankedCounts.toMap(),
    )
}
