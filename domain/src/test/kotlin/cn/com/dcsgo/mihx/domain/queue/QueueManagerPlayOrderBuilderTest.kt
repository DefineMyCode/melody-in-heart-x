package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueManagerPlayOrderBuilderTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")

    @Test
    fun `sequential builds ascending ids`() {
        val songs = listOf(song(1), song(2), song(3))
        val q = QueueManager.PlayOrderBuilder(PlayQueue(songs, 0, PlayMode.SEQUENTIAL, emptyList()))
            .sequential()
            .build()
        assertEquals(listOf(1L, 2L, 3L), q.playOrderIds)
    }

    @Test
    fun `reverse builds descending ids`() {
        val songs = listOf(song(1), song(2), song(3))
        val q = QueueManager.PlayOrderBuilder(PlayQueue(songs, 0, PlayMode.SEQUENTIAL, emptyList()))
            .reverse()
            .build()
        assertEquals(listOf(3L, 2L, 1L), q.playOrderIds)
    }

    @Test
    fun `withIds overrides order`() {
        val songs = listOf(song(1), song(2), song(3))
        val q = QueueManager.PlayOrderBuilder(PlayQueue(songs, 0, PlayMode.SEQUENTIAL, emptyList()))
            .withIds(listOf(3, 1, 2))
            .build()
        assertEquals(listOf(3L, 1L, 2L), q.playOrderIds)
    }
}
