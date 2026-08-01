package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ControllerWindowSynchronizerTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")

    private fun bigQueue(current: Int) = PlayQueue(
        (0L..999L).map { song(it) },
        current,
        PlayMode.SEQUENTIAL,
        (0L..999L).toList(),
    )

    @Test
    fun `first resolve always produces a plan`() {
        val sync = ControllerWindowSynchronizer(WindowedControllerQueuePlanner(PlaybackWindowPlanner()))
        val res = sync.resolve(bigQueue(500))
        assertEquals(500 - 20, res.window.startIndex)
        assertEquals(true, res.plan != null)
    }

    @Test
    fun `safe navigation inside window reuses plan`() {
        val sync = ControllerWindowSynchronizer(WindowedControllerQueuePlanner(PlaybackWindowPlanner()))
        val first = sync.resolve(bigQueue(500))
        val versionAfterFirst = first.window.windowVersion
        // window for current=500: start=480, end=550, size=71; safe range [490, 540]
        val second = sync.resolve(bigQueue(510))
        assertNull(second.plan)
        assertEquals(versionAfterFirst, second.window.windowVersion)
        assertEquals(510 - 480, second.currentIndexInWindow)
    }

    @Test
    fun `approaching edge triggers replan with new version`() {
        val sync = ControllerWindowSynchronizer(WindowedControllerQueuePlanner(PlaybackWindowPlanner()))
        val first = sync.resolve(bigQueue(500))
        val v1 = first.window.windowVersion
        // current=545 -> pos in window = 65 > 60 (size-1-threshold) -> replan
        val second = sync.resolve(bigQueue(545))
        assertEquals(true, second.plan != null)
        assertEquals(v1 + 1, second.window.windowVersion)
    }

    @Test
    fun `forceReplan clears cache`() {
        val sync = ControllerWindowSynchronizer(WindowedControllerQueuePlanner(PlaybackWindowPlanner()))
        sync.resolve(bigQueue(500))
        sync.forceReplan()
        assertNull(sync.current())
    }
}
