package cn.com.dcsgo.mihx.domain.queue

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultRandomQueuePlannerTest {

    private val planner = DefaultRandomQueuePlanner()

    @Test
    fun `empty input stays empty`() {
        assertTrue(planner.plan(emptyList()).isEmpty())
    }

    @Test
    fun `single id stays itself`() {
        assertEquals(listOf(7L), planner.plan(listOf(7L)))
    }

    @Test
    fun `output is a permutation of the input`() {
        val ids = (1L..100L).toList()
        val out = planner.plan(ids)
        assertEquals(ids.size, out.size)
        assertEquals(ids.sorted(), out.sorted())
    }

    @Test
    fun `duplicate ids survive the shuffle`() {
        val ids = listOf(1L, 1L, 2L, 2L, 3L)
        val out = planner.plan(ids)
        assertEquals(ids.sorted(), out.sorted())
    }
}
