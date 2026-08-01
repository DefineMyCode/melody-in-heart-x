package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QueueOperatorTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")

    private val operator = QueueOperator(
        DefaultUniformRandomPlanner(),
        DefaultRandomQueuePlanner(),
    )

    @Test
    fun `addSongAsNext inserts after current anchor`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val updated = operator.addSongAsNext(queue, listOf(song(9)), anchorIndex = 0)
        assertEquals(listOf(1L, 9L, 2L, 3L), updated.playOrderIds)
        assertEquals(0, updated.currentIndex)
        assertEquals(4, updated.songs.size)
    }

    @Test
    fun `addSongAsNext with duplicate id keeps single content entry`() {
        val songs = listOf(song(1), song(2))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2))
        val updated = operator.addSongAsNext(queue, listOf(song(1)), anchorIndex = 0)
        assertEquals(listOf(1L, 1L, 2L), updated.playOrderIds)
        assertEquals(2, updated.songs.size)
        assertEquals(0, updated.currentIndex)
    }

    @Test
    fun `addSongsToTail skips duplicates when allowDuplicates false`() {
        val songs = listOf(song(1), song(2))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2))
        val updated = operator.addSongsToTail(queue, listOf(song(2), song(3)), allowDuplicates = false)
        assertEquals(listOf(1L, 2L, 3L), updated.playOrderIds)
        assertEquals(3, updated.songs.size)
    }

    @Test
    fun `addSongsToTail allows duplicates when requested`() {
        val songs = listOf(song(1))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1))
        val updated = operator.addSongsToTail(queue, listOf(song(1)), allowDuplicates = true)
        assertEquals(listOf(1L, 1L), updated.playOrderIds)
    }

    @Test
    fun `removeAt by queue index adjusts current when removing after it`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 1, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val updated = operator.removeAt(queue, 2)
        assertEquals(listOf(1L, 2L), updated.playOrderIds)
        assertEquals(1, updated.currentIndex)
    }

    @Test
    fun `removeAt current item shifts current to next`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 1, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val updated = operator.removeAt(queue, 1)
        assertEquals(listOf(1L, 3L), updated.playOrderIds)
        assertEquals(1, updated.currentIndex)
    }

    @Test
    fun `switchPlayMode REVERSE preserves current item position`() {
        val songs = listOf(song(1), song(2), song(3), song(4))
        val queue = PlayQueue(songs, 2, PlayMode.SEQUENTIAL, listOf(1, 2, 3, 4))
        val reversed = operator.switchPlayMode(queue, PlayMode.REVERSE)
        assertEquals(listOf(4L, 3L, 2L, 1L), reversed.playOrderIds)
        assertEquals(1, reversed.currentIndex)
    }
}
