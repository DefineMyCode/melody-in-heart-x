package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayQueueRestoreTest {

    private fun song(id: Long) = Song(id, "uri$id", "t$id", "artist", "album", 1000L)

    @Test
    fun `restore preserves repeats and order`() {
        val library = listOf(song(1), song(2), song(3))
        val snapshot = PlaybackStateSnapshot(
            songIds = listOf(1L, 1L, 2L),
            currentIndex = 1,
            playMode = PlayMode.SEQUENTIAL,
            positionMs = 0L,
            currentMediaId = "1",
            savedAt = 0L,
        )
        val queue = QueueRestore.restore(library, snapshot)
        assertEquals(listOf(1L, 1L, 2L), queue.playOrderIds)
        assertEquals(3, queue.songs.size)
        assertEquals(1L, queue.songs[0].id)
        assertEquals(1L, queue.songs[1].id)
        assertEquals(2L, queue.songs[2].id)
        assertEquals(1, queue.currentIndex)
        assertEquals(PlayMode.SEQUENTIAL, queue.playMode)
    }

    @Test
    fun `restore drops missing ids and clamps currentIndex`() {
        val library = listOf(song(1), song(2))
        val snapshot = PlaybackStateSnapshot(
            songIds = listOf(1L, 5L, 2L, 5L),
            currentIndex = 3,
            playMode = PlayMode.REVERSE,
            positionMs = 0L,
            currentMediaId = "5",
            savedAt = 0L,
        )
        val queue = QueueRestore.restore(library, snapshot)
        assertEquals(listOf(1L, 2L), queue.playOrderIds)
        assertEquals(2, queue.songs.size)
        assertEquals(1, queue.currentIndex)
        assertEquals(PlayMode.REVERSE, queue.playMode)
    }

    @Test
    fun `restore falls back to whole library when all ids are missing`() {
        val library = listOf(song(1), song(2))
        val snapshot = PlaybackStateSnapshot(
            songIds = listOf(9L, 8L),
            currentIndex = 1,
            playMode = PlayMode.RANDOM,
            positionMs = 0L,
            currentMediaId = null,
            savedAt = 0L,
        )
        val queue = QueueRestore.restore(library, snapshot)
        assertEquals(library.map { it.id }, queue.playOrderIds)
        assertEquals(0, queue.currentIndex)
        assertEquals(PlayMode.RANDOM, queue.playMode)
    }

    @Test
    fun `restore clamps negative currentIndex`() {
        val library = listOf(song(1))
        val snapshot = PlaybackStateSnapshot(
            songIds = listOf(1L),
            currentIndex = -5,
            playMode = PlayMode.SEQUENTIAL,
            positionMs = 0L,
            currentMediaId = "1",
            savedAt = 0L,
        )
        val queue = QueueRestore.restore(library, snapshot)
        assertEquals(0, queue.currentIndex)
    }
}
