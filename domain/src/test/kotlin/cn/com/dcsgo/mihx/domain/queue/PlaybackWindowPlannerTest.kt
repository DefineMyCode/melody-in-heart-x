package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackWindowPlannerTest {
    private fun song(id: Long, uri: String? = "u$id") = Song(id, uri, "t$id", "ar", "al")

    @Test
    fun `window size never exceeds 71 and includes current`() {
        val full = (0L..999L).map { song(it) }
        val planner = PlaybackWindowPlanner()
        val window = planner.plan(full, 500)
        assertEquals(PlaybackWindowPlanner.MAX_WINDOW_SIZE, window.items.size)
        assertEquals(500 - 20, window.startIndexInFullOrder)
        assertEquals(20, window.currentIndexInWindow)
    }

    @Test
    fun `window at head clamps start to 0`() {
        val full = (0L..999L).map { song(it) }
        val planner = PlaybackWindowPlanner()
        val window = planner.plan(full, 0)
        assertEquals(0, window.startIndexInFullOrder)
        assertEquals(0, window.currentIndexInWindow)
        assertEquals(51, window.items.size)
    }

    @Test
    fun `window at tail clamps end to last index`() {
        val full = (0L..999L).map { song(it) }
        val planner = PlaybackWindowPlanner()
        val window = planner.plan(full, 999)
        // Spec "前20后50≤71 有界窗口": at tail the forward span clamps, so window is [979..999]
        // (21 items), with the current item at position 20. No "fill backward" — the head test
        // already establishes the clamp-but-don't-fill contract (51 items, currentIndexInWindow=0).
        assertEquals(979, window.startIndexInFullOrder)
        assertEquals(20, window.currentIndexInWindow)
        assertEquals(21, window.items.size)
    }

    @Test
    fun `unplayable current snaps forward to nearest playable`() {
        val full = listOf(song(1), song(2, null), song(3, null), song(4))
        val planner = PlaybackWindowPlanner(lookBehind = 0, lookAhead = 0)
        val window = planner.plan(full, 1)
        assertEquals(3, window.currentIndexInFullOrder)
        // lookBehind=0/lookAhead=0 -> window is exactly the snapped current item [song 4],
        // so it sits at position 0 within the window.
        assertEquals(0, window.currentIndexInWindow)
        assertEquals(listOf(4L), window.items.map { it.id })
    }

    @Test
    fun `unplayable neighbours dropped but playable current kept`() {
        val full = listOf(song(1, null), song(2), song(3, null))
        val planner = PlaybackWindowPlanner(lookBehind = 1, lookAhead = 1)
        val window = planner.plan(full, 1)
        assertEquals(listOf(2L), window.items.map { it.id })
        assertEquals(1, window.currentIndexInFullOrder)
    }
}
