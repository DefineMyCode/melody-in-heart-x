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

    private fun sync() = ControllerWindowSynchronizer(
        WindowedControllerQueuePlanner(PlaybackWindowPlanner()),
        WindowSlidePlanner(),
    )

    @Test
    fun `first resolve always produces a plan`() {
        val sync = sync()
        val res = sync.resolve(bigQueue(500))
        assertEquals(500 - 20, res.window.startIndex)
        assertEquals(true, res.plan != null)
    }

    @Test
    fun `safe navigation inside window reuses plan`() {
        val sync = sync()
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
        val sync = sync()
        val first = sync.resolve(bigQueue(500))
        val v1 = first.window.windowVersion
        // current=545 -> pos in window = 65 > 60 (size-1-threshold) -> replan
        val second = sync.resolve(bigQueue(545))
        assertEquals(true, second.plan != null)
        assertEquals(v1 + 1, second.window.windowVersion)
    }

    @Test
    fun `forceReplan clears cache`() {
        val sync = sync()
        sync.resolve(bigQueue(500))
        sync.forceReplan()
        assertNull(sync.current())
    }

    @Test
    fun `drift while safely inside window is a no-op`() {
        val sync = sync()
        val first = sync.resolve(bigQueue(500)) // window [480, 551)
        val res = sync.resolveDrift(bigQueue(505))
        assertEquals(WindowSlide.None, res.slide)
        assertNull(res.plan)
        assertEquals(first.window, res.window)
        assertEquals(505 - 480, res.currentIndexInWindow)
    }

    @Test
    fun `drift near tail edge slides incrementally forward`() {
        val sync = sync()
        sync.resolve(bigQueue(500)) // window [480, 551)
        val res = sync.resolveDrift(bigQueue(545))
        val slide = res.slide as WindowSlide.Incremental
        // new window [525, 596): append playable in [551, 596) = 45, drop head playable in
        // [480, 525) = 45 — no prepend, no tail drop.
        assertEquals(0, slide.prepend.size)
        assertEquals(45, slide.append.size)
        assertEquals(45, slide.dropFromHead)
        assertEquals(0, slide.dropFromTail)
        assertEquals(525, res.window.startIndex)
        assertNull(res.plan)
    }

    @Test
    fun `drift near head edge slides incrementally backward`() {
        val sync = sync()
        sync.resolve(bigQueue(500)) // window [480, 551)
        val res = sync.resolveDrift(bigQueue(485))
        val slide = res.slide as WindowSlide.Incremental
        // new window [465, 536): prepend playable in [465, 480) = 15, drop tail playable in
        // [536, 551) = 15 — no append, no head drop.
        assertEquals(15, slide.prepend.size)
        assertEquals(0, slide.append.size)
        assertEquals(0, slide.dropFromHead)
        assertEquals(15, slide.dropFromTail)
        assertEquals(465, res.window.startIndex)
        assertNull(res.plan)
    }

    @Test
    fun `drift with cold cache returns rebuild plan`() {
        val sync = sync()
        val res = sync.resolveDrift(bigQueue(500))
        assertEquals(WindowSlide.Rebuild, res.slide)
        // window [480, 551): start = 500 - 20, end exclusive = 500 + 50 + 1
        assertEquals(480, res.plan?.windowStartIndex)
        assertEquals(551, res.plan?.windowEndIndexExclusive)
    }
}
