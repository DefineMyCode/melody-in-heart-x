package cn.com.dcsgo.mihx.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayQueueTest {

    @Test
    fun `currentPlayOrderIndices preserves repeated ids in order`() {
        val songs = listOf(
            Song(1, "u1", "A", "x", "al"),
            Song(2, "u2", "B", "x", "al"),
            Song(1, "u1", "A", "x", "al"),
        )
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1L, 2L, 1L))
        assertEquals(listOf(0, 1, 2), queue.currentPlayOrderIndices())
    }

    @Test
    fun `currentPlayOrderIndices reverse order maps correctly`() {
        val songs = listOf(
            Song(1, "u1", "A", "x", "al"),
            Song(2, "u2", "B", "x", "al"),
            Song(3, "u3", "C", "x", "al"),
        )
        val queue = PlayQueue(songs, 0, PlayMode.REVERSE, listOf(3L, 2L, 1L))
        assertEquals(listOf(2, 1, 0), queue.currentPlayOrderIndices())
    }
}
