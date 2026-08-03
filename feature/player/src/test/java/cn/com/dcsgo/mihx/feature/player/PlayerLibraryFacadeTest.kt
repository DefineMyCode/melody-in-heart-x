package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLibraryFacadeTest {

    private var state = PlayerUiState()
    private var snapshot = PlaylistSnapshot(songs = emptyList(), playlists = emptyList())
    private var loaded = false
    private var refreshedAlbumArt = false
    private var listener: (() -> Unit)? = null
    private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val facade = PlayerLibraryFacade(
        updateState = { transform -> state = transform(state) },
        loadPersistedSongs = {
            assertTrue(state.isLoading)
            loaded = true
        },
        loadLibraryCatalog = {
            emptyList<cn.com.dcsgo.mihx.core.model.ArtistEntry>() to
                emptyList<cn.com.dcsgo.mihx.core.model.AlbumEntry>()
        },
        refreshAllAlbumArt = { onFinished ->
            refreshedAlbumArt = true
            snapshot = PlaylistSnapshot(
                songs = songs(2),
                playlists = listOf(playlist(2, "Refreshed")),
            )
            onFinished?.invoke()
        },
        snapshot = { snapshot },
        setSongsChangedListener = { callback -> listener = callback },
        catalogScope = testScope,
        ioDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun loadInitialDataLoadsSnapshotRestoresThenRefreshesAlbumArt() {
        snapshot = PlaylistSnapshot(
            songs = songs(1),
            playlists = listOf(playlist(1, "Initial")),
        )
        var restoredWithInitialSnapshot = false

        runBlocking {
            facade.loadInitialData {
                restoredWithInitialSnapshot = true
                assertEquals(listOf(1), state.songs.map { it.id })
                assertEquals(listOf("Initial"), state.playlists.map { it.name })
                assertFalse(state.isLoading)
            }
        }

        assertTrue(loaded)
        assertTrue(restoredWithInitialSnapshot)
        assertTrue(refreshedAlbumArt)
        assertEquals(listOf(2), state.songs.map { it.id })
        assertEquals(listOf("Refreshed"), state.playlists.map { it.name })
        testScope.cancel()
    }

    @Test
    fun listenForSongChangesRefreshesSnapshot() {
        facade.listenForSongChanges()
        snapshot = PlaylistSnapshot(
            songs = songs(3),
            playlists = listOf(playlist(3, "Changed")),
        )

        listener?.invoke()

        assertEquals(listOf(3), state.songs.map { it.id })
        assertEquals(listOf("Changed"), state.playlists.map { it.name })
        testScope.cancel()
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }

    private fun playlist(id: Int, name: String): Playlist {
        return Playlist(
            id = id,
            name = name,
            songCount = 0,
        )
    }
}
