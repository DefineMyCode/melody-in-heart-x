package cn.com.dcsgo.mihx.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.common.perf.PerfTracer
import cn.com.dcsgo.mihx.feature.player.PlayerQueueFacade
import cn.com.dcsgo.mihx.feature.playlist.PlaylistFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val facade: HomeFacade,
    private val playlistFacade: PlaylistFacade,
    private val playerQueueFacade: PlayerQueueFacade,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            facade.library().collect { songs -> _uiState.update { it.copy(songs = songs) } }
        }
        viewModelScope.launch {
            playlistFacade.observePlaylists().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleSelect(id: Long) = _uiState.update { s ->
        val selected = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
        s.copy(selectedIds = selected)
    }

    /**
     * Selects every song in [visibleIds] (the current query-filtered list, or the whole library
     * when the search box is empty). Tapping again while all of them are already selected clears
     * the selection (select-all / deselect-all toggle).
     */
    fun toggleSelectAll(visibleIds: List<Long>) = _uiState.update { s ->
        val ids = visibleIds.toSet()
        val allSelected = ids.isNotEmpty() && s.selectedIds.containsAll(ids)
        s.copy(selectedIds = if (allSelected) emptySet() else ids)
    }

    fun clearSelection() = _uiState.update { it.copy(selectedIds = emptySet()) }

    fun importTree(treeUri: String) = runImport { onProgress -> facade.importTree(treeUri, onProgress) }

    fun importFiles(uris: List<String>) = runImport { onProgress -> facade.importFiles(uris, onProgress) }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            facade.deleteSongs(ids)
            _uiState.update { it.copy(selectedIds = emptySet()) }
        }
    }

    fun openAddToPlaylistDialog() {
        if (_uiState.value.selectedIds.isNotEmpty()) {
            _uiState.update { it.copy(showAddToPlaylistDialog = true) }
        }
    }

    fun dismissAddToPlaylistDialog() = _uiState.update { it.copy(showAddToPlaylistDialog = false) }

    fun addSelectedToPlaylist(playlistId: Long) {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { playlistFacade.addSong(playlistId, it) }
            _uiState.update { it.copy(selectedIds = emptySet(), showAddToPlaylistDialog = false) }
        }
    }

    /** Appends the selected songs to the play queue tail (plan P5-A leftover). */
    fun addSelectedToQueue() {
        val state = _uiState.value
        if (state.selectedIds.isEmpty()) return
        val songs = state.songs.filter { it.id in state.selectedIds }
        if (songs.isEmpty()) return
        playerQueueFacade.addSongsToTail(songs, allowDuplicates = true)
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    private fun runImport(block: suspend (suspend (Int, Int) -> Unit) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = ImportProgress(0, 0)) }
            val onProgress: suspend (Int, Int) -> Unit = { done, total ->
                _uiState.update { it.copy(importProgress = ImportProgress(done, total)) }
            }
            val before = _uiState.value.songs.size
            val start = System.nanoTime()
            block(onProgress)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            // P5-A: import duration bucketed by the number of songs actually added.
            val imported = _uiState.value.songs.size - before
            PerfTracer.record(importBucketLabel(imported), elapsedMs)
            _uiState.update { it.copy(isImporting = false, importProgress = null) }
        }
    }

    private fun importBucketLabel(count: Int): String = when {
        count <= 0 -> "import"
        count <= 50 -> "import_1_50"
        count <= 200 -> "import_51_200"
        count <= 500 -> "import_201_500"
        else -> "import_501_plus"
    }
}
