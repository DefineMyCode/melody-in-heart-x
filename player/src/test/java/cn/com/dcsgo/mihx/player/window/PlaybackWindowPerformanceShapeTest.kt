package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWindowPerformanceShapeTest {
    @Test
    fun defaultWindowKeepsControllerQueueBoundedForProductLibrarySizes() {
        listOf(100, 500, 1_000).forEach { songCount ->
            val queue = PlayQueue().setQueue(songs(songCount), startIndex = songCount / 2)
            val plan = testPlanner().plan(queue, queue.currentIndex)

            assertEquals(20, plan?.startIndex)
            assertTrue("Controller queue must stay windowed for $songCount songs", (plan?.songs?.size ?: 0) <= 71)
            assertTrue("Controller queue should include the configured next-song window for $songCount songs", (plan?.songs?.size ?: 0) >= 51)
        }
    }

    @Test
    fun defaultWindowBoundsStartMiddleEndForThousandSongQueue() {
        val planner = testPlanner()
        val songs = songs(1_000)

        val startPlan = planner.plan(PlayQueue().setQueue(songs, startIndex = 0), requestedIndex = 0)
        val middlePlan = planner.plan(PlayQueue().setQueue(songs, startIndex = 500), requestedIndex = 500)
        val endPlan = planner.plan(PlayQueue().setQueue(songs, startIndex = 999), requestedIndex = 999)

        assertEquals(51, startPlan?.songs?.size)
        assertEquals(0, startPlan?.startIndex)
        assertEquals(71, middlePlan?.songs?.size)
        assertEquals(20, middlePlan?.startIndex)
        assertEquals(21, endPlan?.songs?.size)
        assertEquals(20, endPlan?.startIndex)
    }

    @Test
    fun shuffledWindowKeepsCurrentSongAndDoesNotExpandToFullQueue() {
        val songs = songs(1_000)
        val orderedIds = (1_000 downTo 1).toList()
        val queue = PlayQueue(
            songs = songs,
            currentIndex = 499,
            playMode = PlayMode.SHUFFLE,
            playOrderIds = orderedIds,
        )

        val plan = testPlanner().plan(queue, requestedIndex = 499)

        assertTrue((plan?.songs?.size ?: 0) <= 71)
        assertTrue((plan?.songs?.size ?: 0) >= 51)
        assertEquals(0, plan?.startIndex)
        assertEquals(500, plan?.songs?.firstOrNull()?.id)
    }

    private fun testPlanner(): WindowedControllerQueuePlanner {
        return WindowedControllerQueuePlanner(
            ControllerWindowSynchronizer(
                PlaybackWindowPlanner(isPlayable = { true }),
            ),
        )
    }

    private fun songs(count: Int): List<Song> = (1..count).map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
