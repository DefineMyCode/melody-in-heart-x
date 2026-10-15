package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-5（评审 2026-10-15）：门面随 QuickSkipActions 一起 suspend 化，
 * 调度责任移交给 PlayerRuntime（scope.launch），门面不再自带 launcher。
 */
class PlayerQuickSkipFacadeTest {

    private val actions = FakeQuickSkipActions()
    private var refreshed = false
    private val facade = PlayerQuickSkipFacade(
        quickSkipActions = actions,
        refreshPlaylists = { refreshed = true },
    )

    @Test
    fun delegatesBasicQuickSkipOperations() = runBlocking {
        actions.storedSongs = songs(1, 2)

        assertEquals(listOf(1, 2), facade.getQuickSkipSongs().map { it.id })

        facade.addToQuickSkipSongs(3)
        assertTrue(actions.songIds.contains(3))

        facade.removeFromQuickSkipSongs(3)
        assertFalse(actions.songIds.contains(3))
    }

    @Test
    fun syncToPlaylistRefreshesPlaylists() = runBlocking {
        facade.syncQuickSkipSongsToPlaylist()

        assertTrue(actions.synced)
        assertTrue(refreshed)
    }

    private class FakeQuickSkipActions : QuickSkipActions {
        var storedSongs = emptyList<Song>()
        val songIds = mutableSetOf<Int>()
        var synced = false

        override suspend fun getSongs(): List<Song> = storedSongs

        override suspend fun add(songId: Int): Boolean = songIds.add(songId)

        override suspend fun remove(songId: Int): Boolean = songIds.remove(songId)

        override suspend fun contains(songId: Int): Boolean = songId in songIds

        override suspend fun syncToPlaylist() {
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
