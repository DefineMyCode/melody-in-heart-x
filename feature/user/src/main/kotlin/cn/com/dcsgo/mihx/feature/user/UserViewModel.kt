package cn.com.dcsgo.mihx.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongPlayStats
import cn.com.dcsgo.mihx.domain.version.SongVersionResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val facade: UserFacade,
    private val versionResolver: SongVersionResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserUiState())
    val uiState: StateFlow<UserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(facade.songs, facade.playStats, facade.groupOverrides) { songs, stats, overrides ->
                buildState(songs, stats, overrides)
            }.collect { _uiState.value = it }
        }
    }

    /**
     * Composes the three live sources into one state: total listening time is derived from the
     * summed per-song [SongPlayStats.totalPlayedMs]; same-title groups (size > 1) carry the user
     * override plus the automatic best version (highest sample rate) for highlighting.
     */
    private fun buildState(
        songs: List<Song>,
        stats: List<SongPlayStats>,
        overrides: Map<String, Long>,
    ): UserUiState {
        val byId = HashMap<Long, Song>(songs.size)
        for (song in songs) byId[song.id] = song

        val groups = HashMap<String, MutableList<Song>>()
        for (song in songs) groups.getOrPut(song.groupKey) { mutableListOf() }.add(song)
        val versionGroups = groups.entries
            .filter { it.value.size > 1 }
            .map { (key, versions) ->
                SongGroupUi(
                    groupKey = key,
                    versions = versions,
                    preferredSongId = overrides[key],
                    autoPreferredSongId = versionResolver.resolve(versions)?.id,
                )
            }
            .sortedBy { it.groupKey }

        val top = stats
            .filter { it.playCount > 0 }
            .sortedByDescending { it.playCount }
            .take(TOP_TRACK_LIMIT)
            .map { it.toRow(byId) }
        val skipped = stats
            .filter { it.skipCount > 0 || it.shortPlayCount > 0 }
            .sortedByDescending { it.skipCount }
            .map { it.toRow(byId) }

        return UserUiState(
            isLoading = false,
            totalPlayedMs = stats.sumOf { it.totalPlayedMs },
            topSongs = top,
            skippedSongs = skipped,
            versionGroups = versionGroups,
        )
    }

    /** Tapping the current preferred version drops the override (back to automatic); tapping any
     * other version pins it as the new preference. */
    fun togglePreferredVersion(groupKey: String, songId: Long) {
        viewModelScope.launch {
            val current = facade.groupOverrides.first()[groupKey]
            if (current == songId) {
                facade.clearPreferredSongId(groupKey)
            } else {
                facade.setPreferredSongId(groupKey, songId)
            }
        }
    }

    private fun SongPlayStats.toRow(byId: Map<Long, Song>): StatRowUi {
        val song = byId[songId]
        return StatRowUi(
            songId = songId,
            title = song?.title ?: "未知曲目",
            artist = song?.artist.orEmpty(),
            albumArtUri = song?.albumArtUri,
            playCount = playCount,
            skipCount = skipCount,
            shortPlayCount = shortPlayCount,
        )
    }

    companion object {
        private const val TOP_TRACK_LIMIT = 10
    }
}
