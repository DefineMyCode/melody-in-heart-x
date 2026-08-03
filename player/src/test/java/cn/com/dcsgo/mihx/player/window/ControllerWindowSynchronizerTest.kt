package cn.com.dcsgo.mihx.player.window

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerWindowSynchronizerTest {

    @Test
    fun planControllerQueueWindowsLargeSequentialQueue() {
        val synchronizer = testSynchronizer(previousCount = 20, nextCount = 50)
        val queue = PlayQueue().setQueue(songs(100), startIndex = 50)

        val plan = synchronizer.planControllerQueue(queue)

        assertEquals((31..100).toList(), plan?.songs?.map { it.id })
        assertEquals(20, plan?.startIndex)
    }

    @Test
    fun planControllerQueueClampsAtStartAndEnd() {
        val synchronizer = testSynchronizer(previousCount = 20, nextCount = 50)

        val startPlan = synchronizer.planControllerQueue(
            PlayQueue().setQueue(songs(100), startIndex = 2),
        )
        val endPlan = synchronizer.planControllerQueue(
            PlayQueue().setQueue(songs(100), startIndex = 95),
        )

        assertEquals((1..53).toList(), startPlan?.songs?.map { it.id })
        assertEquals(2, startPlan?.startIndex)
        assertEquals((76..100).toList(), endPlan?.songs?.map { it.id })
        assertEquals(20, endPlan?.startIndex)
    }

    @Test
    fun planControllerQueueUsesReversePlaybackOrder() {
        val synchronizer = testSynchronizer(previousCount = 2, nextCount = 3)
        val queue = PlayQueue()
            .setQueue(songs(10), startIndex = 7, mode = PlayMode.REVERSE)

        val plan = synchronizer.planControllerQueue(queue)

        assertEquals(listOf(10, 9, 8, 7, 6, 5), plan?.songs?.map { it.id })
        assertEquals(2, plan?.startIndex)
    }

    @Test
    fun planControllerQueueUsesStableShuffleOrder() {
        val orderedIds = listOf(4, 1, 5, 2, 3)
        val queue = PlayQueue(
            songs = songs(5),
            currentIndex = 0,
            playMode = PlayMode.SHUFFLE,
            playOrderIds = orderedIds,
        )
        val synchronizer = testSynchronizer(previousCount = 1, nextCount = 1)

        val plan = synchronizer.planControllerQueue(queue)

        assertEquals(listOf(4, 1, 5), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun nonForcedPlanReusesWindowButMovesStartToCurrentSong() {
        val synchronizer = testSynchronizer(previousCount = 1, nextCount = 3)
        val queue = PlayQueue().setQueue(songs(10), startIndex = 3)

        synchronizer.planControllerQueue(queue, force = true)
        val reusedPlan = synchronizer.planControllerQueue(queue.withCurrentIndex(5), force = false)

        assertEquals(listOf(3, 4, 5, 6, 7), reusedPlan?.songs?.map { it.id })
        assertEquals(3, reusedPlan?.startIndex)
    }

    @Test
    fun forcedPlanAfterAddNextInvalidatesWindow() {
        val synchronizer = testSynchronizer(previousCount = 1, nextCount = 2)
        val queue = PlayQueue()
            .setQueue(songs(1, 2, 3, 4), startIndex = 1)
        synchronizer.planControllerQueue(queue, force = true)

        val inserted = queue.addSongAsNext(song(5))
        val plan = synchronizer.planControllerQueue(inserted, force = true)

        assertEquals(listOf(1, 2, 5, 3), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun planControllerQueueReturnsNullForEmptyQueue() {
        val synchronizer = testSynchronizer()

        assertNull(synchronizer.planControllerQueue(PlayQueue()))
    }

    private fun testSynchronizer(
        previousCount: Int = PlaybackWindowPlanner.DEFAULT_PREVIOUS_COUNT,
        nextCount: Int = PlaybackWindowPlanner.DEFAULT_NEXT_COUNT,
    ): ControllerWindowSynchronizer {
        return ControllerWindowSynchronizer(
            PlaybackWindowPlanner(
                previousCount = previousCount,
                nextCount = nextCount,
                isPlayable = { true },
            ),
        )
    }

    private fun songs(count: Int): List<Song> = (1..count).map(::song)

    private fun songs(vararg ids: Int): List<Song> = ids.map(::song)

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
