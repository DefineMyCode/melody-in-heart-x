package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowedControllerQueuePlannerTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")

    @Test
    fun `plan returns bounded window with correct current index`() {
        val songs = (0L..999L).map { song(it) }
        val queue = PlayQueue(songs, 500, PlayMode.SEQUENTIAL, (0L..999L).toList())
        val planner = WindowedControllerQueuePlanner(PlaybackWindowPlanner())
        val plan = planner.plan(queue)
        assertEquals(PlaybackWindowPlanner.MAX_WINDOW_SIZE, plan.mediaItems.size)
        assertEquals(20, plan.currentIndex)
        assertEquals(500 - 20, plan.windowStartIndex)
        assertEquals("500", plan.mediaItems[plan.currentIndex].mediaId)
    }

    @Test
    fun `plan mediaId and song match`() {
        val songs = listOf(song(7), song(8))
        val queue = PlayQueue(songs, 1, PlayMode.SEQUENTIAL, listOf(7, 8))
        val planner = WindowedControllerQueuePlanner(PlaybackWindowPlanner())
        val plan = planner.plan(queue)
        assertEquals("8", plan.mediaItems[1].mediaId)
        assertEquals(8L, plan.mediaItems[1].song.id)
    }
}
