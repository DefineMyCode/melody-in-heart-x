package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SongDeletionCoordinatorTest {

    private var deleteResult: DeleteSongResult = DeleteSongResult.Failure("missing")
    private val deletedSongIds = mutableListOf<Int>()
    private val coordinator = SongDeletionCoordinator { songId ->
        deletedSongIds += songId
        deleteResult
    }

    @Test
    fun successfulDeleteRequestsQueueRemovalAndRefresh() = kotlinx.coroutines.runBlocking {
        val result = DeleteSongResult.Success(song(7), message = "deleted")
        deleteResult = result

        val plan = coordinator.delete(7)

        assertSame(result, plan.result)
        assertEquals(listOf(7), deletedSongIds)
        assertEquals(7, plan.removeFromQueueSongId)
        assertTrue(plan.shouldRefreshLibrary)
    }

    @Test
    fun failedDeleteDoesNotRequestFollowUpActions() = kotlinx.coroutines.runBlocking {
        val result = DeleteSongResult.Failure("no permission")
        deleteResult = result

        val plan = coordinator.delete(7)

        assertSame(result, plan.result)
        assertEquals(listOf(7), deletedSongIds)
        assertNull(plan.removeFromQueueSongId)
        assertFalse(plan.shouldRefreshLibrary)
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
