package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerQueuePlannerTest {

    @Test
    fun sequentialPlanStartsAtRequestedSong() {
        val songs = songs(1, 2, 3)
        val queue = PlayQueue().setQueue(songs, startIndex = 1, mode = PlayMode.SEQUENTIAL)

        val plan = plan(queue)

        assertEquals(listOf(1, 2, 3), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
        assertEquals(1, plan?.remainingAfterStart)
    }

    @Test
    fun duplicateSongsRemainInControllerQueue() {
        val queue = PlayQueue().setQueue(songs(1, 2, 2, 3), startIndex = 0, mode = PlayMode.SEQUENTIAL)

        val plan = plan(queue)

        assertEquals(listOf(1, 2, 2, 3), plan?.songs?.map { it.id })
    }

    @Test
    fun duplicateSongStartIndexUsesRequestedQueueOccurrence() {
        val queue = PlayQueue().setQueue(songs(1, 2, 2, 3), startIndex = 2, mode = PlayMode.SEQUENTIAL)

        val plan = plan(queue, requestedIndex = 2)

        assertEquals(listOf(1, 2, 2, 3), plan?.songs?.map { it.id })
        assertEquals(2, plan?.startIndex)
    }

    @Test
    fun reversePlanUsesControllerPlaybackOrder() {
        val songs = songs(1, 2, 3)
        val queue = PlayQueue().setQueue(songs, startIndex = 2, mode = PlayMode.REVERSE)

        val plan = plan(queue)

        assertEquals(listOf(3, 2, 1), plan?.songs?.map { it.id })
        assertEquals(0, plan?.startIndex)
    }

    @Test
    fun addAsNextPlacesSongAfterCurrentInControllerOrder() {
        val songs = songs(1, 2, 3)
        val inserted = song(4)
        val queue = PlayQueue()
            .setQueue(songs, startIndex = 1, mode = PlayMode.SEQUENTIAL)
            .addSongAsNext(inserted)

        val plan = plan(queue)

        assertEquals(listOf(1, 2, 4, 3), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun changedPlayModeRebuildsControllerOrder() {
        val songs = songs(1, 2, 3)
        val queue = PlayQueue()
            .setQueue(songs, startIndex = 1, mode = PlayMode.SEQUENTIAL)
            .setPlayMode(PlayMode.REVERSE)

        val plan = plan(queue)

        assertEquals(listOf(3, 2, 1), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun requestedUnplayableSongStartsAtNextPlayableItem() {
        val songs = songs(1, 2, 3)
        val queue = PlayQueue().setQueue(songs, startIndex = 1, mode = PlayMode.SEQUENTIAL)

        val plan = plan(queue, playableIds = setOf(1, 3))

        assertEquals(listOf(1, 3), plan?.songs?.map { it.id })
        assertEquals(1, plan?.startIndex)
    }

    @Test
    fun allUnplayableSongsReturnNull() {
        val queue = PlayQueue().setQueue(
            songs(1, 2),
            startIndex = 0,
            mode = PlayMode.SEQUENTIAL
        )

        assertNull(plan(queue, playableIds = emptySet()))
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun plan(
        queue: PlayQueue,
        requestedIndex: Int = queue.currentIndex,
        playableIds: Set<Int> = queue.songs.map { it.id }.toSet(),
    ): ControllerQueuePlan? {
        return ControllerQueuePlanner.plan(queue, requestedIndex) { it.id in playableIds }
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            uri = null,
        )
    }
}
