package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerLibraryFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val loadPersistedSongs: suspend () -> Unit,
    private val loadLibraryCatalog: suspend () -> Pair<List<ArtistEntry>, List<AlbumEntry>>,
    private val refreshAllAlbumArt: suspend (onFinished: (() -> Unit)?) -> Unit,
    private val snapshot: () -> PlaylistSnapshot,
    private val setSongsChangedListener: (() -> Unit) -> Unit,
    private val catalogScope: CoroutineScope,
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
        // 同步刷新曲库歌手/专辑目录（从持久化表查询）
        catalogScope.launch {
            val (artists, albums) = loadLibraryCatalog()
            updateState { state ->
                state.copy(
                    libraryArtists = artists,
                    libraryAlbums = albums,
                )
            }
        }
    }
}
