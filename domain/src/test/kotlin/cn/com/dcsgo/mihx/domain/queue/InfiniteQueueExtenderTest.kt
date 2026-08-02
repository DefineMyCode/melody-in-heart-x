package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfiniteQueueExtenderTest {
    private fun song(id: Long, uri: String? = "u$id") = Song(id, uri, "t$id", "ar", "al")

    private fun queue(vararg ids: Long) = PlayQueue(
        songs = ids.map { song(it) },
        currentIndex = 0,
        playMode = PlayMode.SEQUENTIAL,
        playOrderIds = ids.toList(),
    )

    private fun extender() = InfiniteQueueExtender(DefaultRandomQueuePlanner())

    @Test
    fun `empty library yields empty batch`() {
        assertTrue(extender().nextBatch(queue(1L), emptyList()).isEmpty())
    }

    @Test
    fun `batch is capped and never repeats within itself`() {
        val library = (0L..199L).map { song(it) }
        val batch = extender().nextBatch(queue(), library, batchSize = 50)
        assertEquals(50, batch.size)
        // Plain set-insert loop (gate A5: no .distinct/.associateBy/.toSet even in tests).
        val seen = HashSet<Long>()
        var unique = true
        for (id in batch.map { it.id }) {
            if (!seen.add(id)) unique = false
        }
        assertTrue(unique)
    }

    @Test
    fun `uncovered library songs are preferred`() {
        val library = (0L..99L).map { song(it) }
        val batch = extender().nextBatch(queue(0L, 1L, 2L), library, batchSize = 50)
        val ids = batch.map { it.id }
        assertTrue(ids.all { it >= 3L })
    }

    @Test
    fun `once everything is covered the library loops`() {
        val library = (0L..9L).map { song(it) }
        val covered = queue(*LongArray(10) { it.toLong() })
        val batch = extender().nextBatch(covered, library, batchSize = 5)
        assertEquals(5, batch.size)
    }

    @Test
    fun `unplayable library songs are never picked`() {
        val library = listOf(song(1), song(2, null), song(3))
        val batch = extender().nextBatch(queue(), library, batchSize = 5)
        assertEquals(listOf(1L, 3L).sorted(), batch.map { it.id }.sorted())
    }
}
