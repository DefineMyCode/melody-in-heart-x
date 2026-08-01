package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * P3-10 consistency regression for the queue operations that every trigger surface ultimately
 * relies on.
 *
 * The four surfaces — App UI buttons, Media3 notification, lock-screen, and headset media keys —
 * all drive `PlayerTransportFacade.seekToNext/seekToPrevious` (which maps to
 * `ExoPlayer.seekToNextMediaItem()` / `seekToPreviousMediaItem()`, a direct media-item jump with no
 * 3s-rewind semantics) for prev/next, and `PlayerQueueFacade.addSongAsNext` for "add to next".
 * Structural changes funnel through [QueueOperator]. A correct contract here therefore guarantees
 * end-to-end consistency across all four surfaces.
 */
class QueueConsistencyRegressionTest {
    private fun song(id: Long) = Song(id, "u$id", "t$id", "ar", "al")
    private val operator = QueueOperator(DefaultUniformRandomPlanner(), DefaultRandomQueuePlanner())

    // --- prev / next via jumpTo(current ± 1): the shared contract for all four surfaces ---

    @Test
    fun `next at last item stays at last (no implicit wrap in SEQUENTIAL)`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 2, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val next = operator.jumpTo(queue, queue.currentIndex + 1)
        assertEquals(2, next.currentIndex)
        assertEquals(3L, next.playOrderIds[next.currentIndex])
    }

    @Test
    fun `previous at first item stays at first`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val prev = operator.jumpTo(queue, queue.currentIndex - 1)
        assertEquals(0, prev.currentIndex)
        assertEquals(1L, prev.playOrderIds[prev.currentIndex])
    }

    @Test
    fun `next then previous returns to the same item`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 1, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val next = operator.jumpTo(queue, queue.currentIndex + 1)
        val back = operator.jumpTo(next, next.currentIndex - 1)
        assertEquals(1, back.currentIndex)
        assertEquals(2L, back.playOrderIds[back.currentIndex])
    }

    // --- add-to-next then next must hit the inserted song (P3-10 "添加到下一首") ---

    @Test
    fun `addSongAsNext then next hits the inserted song`() {
        val songs = listOf(song(1), song(2), song(3))
        val queue = PlayQueue(songs, 0, PlayMode.SEQUENTIAL, listOf(1, 2, 3))
        val added = operator.addSongAsNext(queue, listOf(song(9)), anchorIndex = 0)
        val next = operator.jumpTo(added, added.currentIndex + 1)
        assertEquals(1, next.currentIndex)
        assertEquals(9L, next.playOrderIds[next.currentIndex])
    }

    // --- repeated Song.id: jumpTo must land on the played position, never the first occurrence ---

    @Test
    fun `jumpTo lands on the exact played index even with repeated ids`() {
        // Two content entries share id=1. Highlight must follow the play-order index, not the first
        // id match (guards P2-9 / R5).
        val songs = listOf(song(1), song(2), song(1))
        val queue = PlayQueue(songs, 2, PlayMode.SEQUENTIAL, listOf(1, 2, 1))
        val jumped = operator.jumpTo(queue, 2)
        assertEquals(2, jumped.currentIndex)
        assertEquals(1L, jumped.playOrderIds[2])
    }

    // --- RANDOM keeps the currently playing item at the current position ---

    @Test
    fun `switchPlayMode RANDOM keeps current item at current position`() {
        val songs = listOf(song(1), song(2), song(3), song(4))
        val queue = PlayQueue(songs, 2, PlayMode.SEQUENTIAL, listOf(1, 2, 3, 4))
        val random = operator.switchPlayMode(queue, PlayMode.RANDOM)
        assertEquals(PlayMode.RANDOM, random.playMode)
        assertEquals(3L, random.playOrderIds[random.currentIndex]) // current id (3) preserved
    }
}
