package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackWindowPlannerTest {
    @Test
    fun planReturnsNullForEmptyQueue() {
        assertNull(testPlanner().plan(PlayQueue()))
    }

    @Test
    fun planClampsWindowAtQueueStart() {
        val queue = PlayQueue().setQueue(songs(10), startIndex = 2)

        val state = testPlanner(previousCount = 20, nextCount = 50).plan(queue)

        assertEquals(0, state?.fullQueueStartIndex)
        assertEquals(2, state?.controllerStartIndex)
        assertEquals(10, state?.songs?.size)
    }

    @Test
    fun planUsesConfiguredMiddleWindow() {
        val queue = PlayQueue().setQueue(songs(100), startIndex = 50)

        val state = testPlanner(previousCount = 20, nextCount = 50).plan(queue)

        assertEquals(30, state?.fullQueueStartIndex)
        assertEquals(20, state?.controllerStartIndex)
        assertEquals(70, state?.songs?.size)
    }

    @Test
    fun planClampsWindowAtQueueEnd() {
        val queue = PlayQueue().setQueue(songs(100), startIndex = 95)

        val state = testPlanner(previousCount = 20, nextCount = 50).plan(queue)

        assertEquals(75, state?.fullQueueStartIndex)
        assertEquals(20, state?.controllerStartIndex)
        assertEquals(25, state?.songs?.size)
    }

    @Test
    fun planUsesControllerPlaybackOrderForReverseMode() {
        val queue = PlayQueue().setQueue(songs(5), startIndex = 4).setPlayMode(cn.com.dcsgo.mihx.core.model.PlayMode.REVERSE)

        val state = testPlanner(previousCount = 1, nextCount = 2).plan(queue)

        assertEquals(listOf(5, 4, 3), state?.songs?.map { it.id })
        assertEquals(0, state?.controllerStartIndex)
    }

    private fun testPlanner(
        previousCount: Int = PlaybackWindowPlanner.DEFAULT_PREVIOUS_COUNT,
        nextCount: Int = PlaybackWindowPlanner.DEFAULT_NEXT_COUNT,
    ): PlaybackWindowPlanner {
        return PlaybackWindowPlanner(
            previousCount = previousCount,
            nextCount = nextCount,
            isPlayable = { true },
        )
    }

    private fun songs(count: Int): List<Song> {
        return (1..count).map { id ->
            Song(
                id = id,
                title = "Song $id",
                artist = "Artist",
            )
        }
    }
}
