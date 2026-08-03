package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerQuickSkipFacadeTest {

    private val actions = FakeQuickSkipActions()
    private var refreshed = false
    private val launchedTasks = mutableListOf<() -> Unit>()
    private val facade = PlayerQuickSkipFacade(
        quickSkipActions = actions,
        refreshPlaylists = { refreshed = true },
        launch = { task -> launchedTasks += task },
    )

    @Test
    fun delegatesBasicQuickSkipOperations() {
        actions.storedSongs = songs(1, 2)

        assertEquals(listOf(1, 2), facade.getQuickSkipSongs().map { it.id })

        facade.addToQuickSkipSongs(3)
        assertTrue(actions.songIds.contains(3))

        facade.removeFromQuickSkipSongs(3)
        assertFalse(actions.songIds.contains(3))
    }

    @Test
    fun syncToPlaylistRunsInLauncherAndRefreshesPlaylists() {
        facade.syncQuickSkipSongsToPlaylist()

        assertEquals(1, launchedTasks.size)
        launchedTasks.single().invoke()

        assertTrue(actions.synced)
        assertTrue(refreshed)
    }

    private class FakeQuickSkipActions : QuickSkipActions {
        var storedSongs = emptyList<Song>()
        val songIds = mutableSetOf<Int>()
        var synced = false

        override fun getSongs(): List<Song> = storedSongs

        override fun add(songId: Int): Boolean = songIds.add(songId)

        override fun remove(songId: Int): Boolean = songIds.remove(songId)

        override fun contains(songId: Int): Boolean = songId in songIds

        override fun syncToPlaylist() {
            synced = true
        }
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
