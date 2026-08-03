package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerLibraryFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val loadPersistedSongs: suspend () -> Unit,
    private val refreshAllAlbumArt: suspend (onFinished: (() -> Unit)?) -> Unit,
    private val snapshot: () -> PlaylistSnapshot,
    private val setSongsChangedListener: (() -> Unit) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun loadInitialData(afterInitialSnapshot: () -> Unit) {
        updateState { it.copy(isLoading = true) }
        withContext(ioDispatcher) {
            loadPersistedSongs()
        }
        refreshSnapshot(isLoading = false)

        afterInitialSnapshot()

        refreshAllAlbumArt {
            refreshSnapshot()
        }
    }

    fun listenForSongChanges() {
        setSongsChangedListener {
            refreshSnapshot()
        }
    }

    private fun refreshSnapshot(isLoading: Boolean? = null) {
        val currentSnapshot = snapshot()
        updateState { state ->
            state.copy(
                songs = currentSnapshot.songs,
                playlists = currentSnapshot.playlists,
                isLoading = isLoading ?: state.isLoading,
            )
        }
    }
}
