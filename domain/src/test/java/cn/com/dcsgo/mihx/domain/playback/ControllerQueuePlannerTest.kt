package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerQueuePlannerTest {
    @Test
    fun planReturnsNullForEmptyQueue() {
        assertNull(ControllerQueuePlanner.plan(PlayQueue(), isPlayable = { true }))
    }

    @Test
    fun planKeepsSequentialOrderAndRequestedStart() {
        val queue = PlayQueue().setQueue(songs(4), startIndex = 0)

        val plan = ControllerQueuePlanner.plan(queue, requestedIndex = 2, isPlayable = { true })

        assertEquals(listOf(1, 2, 3, 4), plan?.songs?.map { it.id })
        assertEquals(2, plan?.startIndex)
    }

    @Test
    fun planUsesReverseOrder() {
        val queue = PlayQueue()
            .setQueue(songs(5), startIndex = 4)
            .setPlayMode(PlayMode.REVERSE)

        val plan = ControllerQueuePlanner.plan(queue, requestedIndex = 4, isPlayable = { true })

        assertEquals(listOf(5, 4, 3, 2, 1), plan?.songs?.map { it.id })
        assertEquals(0, plan?.startIndex)
    }

    @Test
    fun planUsesStableShuffleOrder() {
        val queue = PlayQueue(
            songs = songs(5),
            currentIndex = 0,
            playMode = PlayMode.SHUFFLE,
            playOrderIds = listOf(1, 4, 5, 2, 3),
        )

        val plan = ControllerQueuePlanner.plan(queue, requestedIndex = 0, isPlayable = { true })

        assertEquals(listOf(1, 4, 5, 2, 3), plan?.songs?.map { it.id })
        assertEquals(0, plan?.startIndex)
    }

    @Test
    fun planSkipsUnplayableSongsAndStartsAtNextPlayable() {
        val queue = PlayQueue().setQueue(songs(5), startIndex = 0)

        val plan = ControllerQueuePlanner.plan(
            queue = queue,
            requestedIndex = 1,
            isPlayable = { song -> song.id != 2 },
        )

        assertEquals(listOf(1, 3, 4, 5), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    private fun songs(count: Int): List<Song> = (1..count).map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
