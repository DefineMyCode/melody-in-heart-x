package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.SongDeletionActions
import cn.com.dcsgo.mihx.domain.model.SongDeletionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSongDeletionFacadeTest {

    private var deleteResult: DeleteSongResult = DeleteSongResult.Failure("missing")
    private val deletedSongIds = mutableListOf<Int>()
    private val removedQueueSongIds = mutableListOf<Int>()
    private var refreshed = false
    private val facade = PlayerSongDeletionFacade(
        songDeletionCoordinator = object : SongDeletionActions {
            override fun delete(songId: Int): SongDeletionPlan {
                deletedSongIds += songId
                return when (deleteResult) {
                    is DeleteSongResult.Success -> SongDeletionPlan(
                        result = deleteResult,
                        removeFromQueueSongId = songId,
                        shouldRefreshLibrary = true,
                    )
                    is DeleteSongResult.Failure -> SongDeletionPlan(deleteResult)
                }
            }
        },
        removeFromPlayQueue = { removedQueueSongIds += it },
        refreshPlaylists = { refreshed = true },
    )

    @Test
    fun successfulDeleteRemovesQueueItemAndRefreshesPlaylists() {
        val result = DeleteSongResult.Success(song(7), message = "deleted")
        deleteResult = result

        val actual = facade.deleteSong(7)

        assertSame(result, actual)
        assertEquals(listOf(7), deletedSongIds)
        assertEquals(listOf(7), removedQueueSongIds)
        assertTrue(refreshed)
    }

    @Test
    fun failedDeleteReturnsFailureWithoutFollowUpActions() {
        val result = DeleteSongResult.Failure("no permission")
        deleteResult = result

        val actual = facade.deleteSong(7)

        assertSame(result, actual)
        assertEquals(listOf(7), deletedSongIds)
        assertEquals(emptyList<Int>(), removedQueueSongIds)
        assertFalse(refreshed)
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
