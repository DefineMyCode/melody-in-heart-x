package cn.com.dcsgo.mihx.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            facade.library().collect { songs -> _uiState.update { it.copy(songs = songs) } }
        }
    }

    fun onQueryChange(q: String) = _uiState.update { it.copy(query = q) }

    fun toggleSelect(id: Long) = _uiState.update { s ->
        val selected = if (id in s.selectedIds) s.selectedIds - id else s.selectedIds + id
        s.copy(selectedIds = selected)
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

    private fun runImport(block: suspend (suspend (Int, Int) -> Unit) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importProgress = ImportProgress(0, 0)) }
            val onProgress: suspend (Int, Int) -> Unit = { done, total ->
                _uiState.update { it.copy(importProgress = ImportProgress(done, total)) }
            }
            block(onProgress)
            _uiState.update { it.copy(isImporting = false, importProgress = null) }
        }
    }
}
