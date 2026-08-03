package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playlist.PlaylistActions
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaylistFacadeTest {

    private val manager = FakePlaylistActions()
    private var state = PlayerUiState()
    private val facade = PlayerPlaylistFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        playlistManager = manager,
    )

    @Test
    fun getSongsByPlaylistUsesCurrentUiSongSnapshot() {
        state = state.copy(songs = songs(1, 2, 3))
        val playlist = Playlist(
            id = 1,
            name = "List",
            songCount = 2,
            songIds = mutableListOf(3, 1),
        )

        val result = facade.getSongsByPlaylist(playlist)

        assertEquals(listOf(1, 3), result.map { it.id })
    }

    @Test
    fun createPlaylistRefreshesUiSnapshot() {
        val playlist = facade.createPlaylist("Favorites")

        assertEquals("Favorites", playlist?.name)
        assertEquals(listOf("Favorites"), state.playlists.map { it.name })
    }

    @Test
    fun renamePlaylistRefreshesOnlyOnSuccess() {
        val playlist = facade.createPlaylist("Old") ?: error("playlist missing")

        val renamed = facade.renamePlaylist(playlist.id, "New")
        val failed = facade.renamePlaylist(999, "Missing")

        assertTrue(renamed)
        assertFalse(failed)
        assertEquals(listOf("New"), state.playlists.map { it.name })
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }

    private class FakePlaylistActions : PlaylistActions {
        private val playlists = mutableListOf<Playlist>()
        private var nextPlaylistId = 1

        override fun getSongsByPlaylist(playlist: Playlist, songs: List<Song>): List<Song> {
            val songIds = playlist.songIds.toSet()
            return songs.filter { it.id in songIds }
        }

        override fun createPlaylist(name: String): Playlist? {
            return Playlist(
                id = nextPlaylistId++,
                name = name,
                songCount = 0,
            ).also { playlists += it }
        }

        override fun deletePlaylist(playlistId: Int) {
            playlists.removeIf { it.id == playlistId }
        }

        override fun renamePlaylist(playlistId: Int, newName: String): Boolean {
            val index = playlists.indexOfFirst { it.id == playlistId }
            if (index < 0) return false
            playlists[index] = playlists[index].copy(name = newName)
            return true
        }

        override fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
            val playlist = playlists.find { it.id == playlistId } ?: return false
            if (songId in playlist.songIds) return false
            playlist.songIds += songId
            return true
        }

        override fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
            playlists.find { it.id == playlistId }?.songIds?.remove(songId)
        }

        override fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
            return playlists.find { it.id == playlistId }?.songIds?.contains(songId) == true
        }

        override fun snapshot(): PlaylistSnapshot {
            return PlaylistSnapshot(
                songs = emptyList(),
                playlists = playlists.map { it.copy(songIds = it.songIds.toMutableList()) },
            )
        }
    }
}
