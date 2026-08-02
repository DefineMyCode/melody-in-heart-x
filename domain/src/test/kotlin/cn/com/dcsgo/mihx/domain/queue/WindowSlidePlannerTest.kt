package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WindowSlidePlannerTest {
    private fun song(id: Long, uri: String? = "u$id") = Song(id, uri, "t$id", "ar", "al")

    private val full = (0L..999L).map { song(it) }

    @Test
    fun `identical ranges produce no slide`() {
        val slide = WindowSlidePlanner().plan(full, 480, 551, 480, 551)
        assertEquals(WindowSlide.None, slide)
    }

    @Test
    fun `forward slide appends and drops from head only`() {
        val slide = WindowSlidePlanner().plan(full, 480, 551, 525, 596) as WindowSlide.Incremental
        assertEquals(0, slide.prepend.size)
        assertEquals(45, slide.append.size)
        assertEquals(45, slide.dropFromHead)
        assertEquals(0, slide.dropFromTail)
    }

    @Test
    fun `backward slide prepends and drops from tail only`() {
        val slide = WindowSlidePlanner().plan(full, 480, 551, 465, 536) as WindowSlide.Incremental
        assertEquals(15, slide.prepend.size)
        assertEquals(0, slide.append.size)
        assertEquals(0, slide.dropFromHead)
        assertEquals(15, slide.dropFromTail)
    }

    @Test
    fun `disjoint ranges require rebuild`() {
        val slide = WindowSlidePlanner().plan(full, 0, 71, 500, 571)
        assertEquals(WindowSlide.Rebuild, slide)
    }

    @Test
    fun `empty old range requires rebuild`() {
        assertEquals(WindowSlide.Rebuild, WindowSlidePlanner().plan(full, 0, 0, 0, 71))
    }

    @Test
    fun `unplayable items are excluded from delta lists and counts`() {
        // Every 5th song is unplayable; only playable survivors count toward drop/append.
        val mixed = (0L..9L).map { if (it % 5 == 0L) song(it, null) else song(it) }
        val slide = WindowSlidePlanner().plan(mixed, 0, 10, 3, 10) as WindowSlide.Incremental
        // Playable in [0, 3): ids 0 (unplayable), 1, 2 -> 2 dropped from head.
        assertEquals(2, slide.dropFromHead)
        // Nothing beyond the old end -> no append.
        assertEquals(0, slide.append.size)
    }
}
