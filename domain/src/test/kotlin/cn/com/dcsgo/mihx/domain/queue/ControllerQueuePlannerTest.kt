package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ControllerQueuePlannerTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")

    @Test
    fun `expand sequential preserves order`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        assertEquals(songs, ControllerQueuePlanner.expand(queue))
    }

    @Test
    fun `expand preserves duplicate positions`() {
        val songs = listOf(song(1), song(2), song(1))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2, 1))
        val expanded = ControllerQueuePlanner.expand(queue)
        assertEquals(3, expanded.size)
        assertEquals(1L, expanded[0].id)
        assertEquals(2L, expanded[1].id)
        assertEquals(1L, expanded[2].id)
        // the second occurrence maps back to queue index 2, not the first one
        assertEquals(2, queue.currentPlayOrderIndices()[2])
    }

    @Test
    fun `expand reverse`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 0, PlayMode.REVERSE, listOf(3, 2, 1))
        assertEquals(listOf(song(3), song(2), song(1)), ControllerQueuePlanner.expand(queue))
    }
}
