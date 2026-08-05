package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 全库封面校验延后启动的延迟，避开首屏渲染与用户早期操作 */
private const val ALBUM_ART_REFRESH_DELAY_MS = 2_000L

class PlayerLibraryFacade(
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val loadPersistedSongs: suspend () -> Unit,
    private val loadLibraryCatalog: suspend () -> Pair<List<ArtistEntry>, List<AlbumEntry>>,
    private val refreshAllAlbumArt: suspend (onFinished: (() -> Unit)?) -> Unit,
    private val snapshot: () -> PlaylistSnapshot,
    private val setSongsChangedListener: (() -> Unit) -> Unit,
    private val catalogScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val albumArtRefreshDelayMs: Long = ALBUM_ART_REFRESH_DELAY_MS,
) {
    suspend fun loadInitialData(afterInitialSnapshot: () -> Unit) {
        updateState { it.copy(isLoading = true) }
        withContext(ioDispatcher) {
            loadPersistedSongs()
        }
        refreshSnapshot(isLoading = false)

        afterInitialSnapshot()

        // 专辑封面校验延后到低优先级后台，避免与首屏交互/播放抢占 CPU 与 IO
        scheduleAlbumArtRefresh()
    }

    /** 延迟 + 单线程 IO 执行全库封面校验，不阻塞启动路径 */
    private fun scheduleAlbumArtRefresh() {
        catalogScope.launch(Dispatchers.IO.limitedParallelism(1)) {
            delay(albumArtRefreshDelayMs)
            try {
                refreshAllAlbumArt {
                    refreshSnapshot()
                }
            } catch (e: Exception) {
                AppLog.error("PlayerLibraryFacade", "refreshAllAlbumArt failed", e)
            }
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
            try {
                val (artists, albums) = loadLibraryCatalog()
                updateState { state ->
                    state.copy(
                        libraryArtists = artists,
                        libraryAlbums = albums,
                    )
                }
            } catch (e: Exception) {
                AppLog.error("PlayerLibraryFacade", "loadLibraryCatalog failed", e)
            }
        }
    }
}
