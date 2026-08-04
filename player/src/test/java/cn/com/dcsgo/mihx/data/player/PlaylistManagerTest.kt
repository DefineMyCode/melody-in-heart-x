package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistManagerTest {

    @Test
    fun getSongsByPlaylistKeepsPlaylistOrderAndFiltersMissingIds() {
        val playlist = Playlist(
            id = 1,
            name = "Favorites",
            songCount = 3,
            songIds = mutableListOf(3, 1, 99),
        )
        val songs = listOf(song(1), song(2), song(3))

        val result = PlaylistManager.getSongsByPlaylist(playlist, songs)

        assertEquals(listOf(3, 1), result.map { it.id })
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
