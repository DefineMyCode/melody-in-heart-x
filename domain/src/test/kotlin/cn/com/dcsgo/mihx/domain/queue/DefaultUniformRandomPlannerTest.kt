package cn.com.dcsgo.mihx.domain.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultUniformRandomPlannerTest {

    @Test
    fun `empty input returns empty`() {
        val planner = DefaultUniformRandomPlanner()
        assertEquals(emptyList<Long>(), planner.plan(emptyList(), emptyMap()))
    }

    @Test
    fun `result is a permutation of input`() {
        val planner = DefaultUniformRandomPlanner()
        val ids = (1L..10L).toList()
        val result = planner.plan(ids, emptyMap())
        assertEquals(ids.size, result.size)
        assertEquals(ids.sorted(), result.sorted())
    }

    @Test
    fun `lower play count biased earlier on average`() {
        val planner = DefaultUniformRandomPlanner()
        val ids = listOf(1L, 2L)
        val counts = mapOf(1L to 1000L, 2L to 0L)
        var song2First = 0
        repeat(200) {
            if (planner.plan(ids, counts).first() == 2L) song2First++
        }
        assertTrue(song2First > 150, "low-count song2 should usually be first, was $song2First/200")
    }
}
