package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomQueuePlannerTest {

    private val planner = RandomQueuePlanner(
        batchSize = 3,
        shuffle = { it },
        isPlayable = { it.sampleRate > 0 },
    )

    @Test
    fun randomQueueSkipsRecentSongsAndRecordsSelection() {
        val plan = planner.planRandomQueue(
            songs = songs(1, 2, 3, 4),
            recentSongIds = setOf(1),
        )

        assertEquals(listOf(2, 3, 4), plan?.songs?.map { it.id })
        assertEquals(setOf(1, 2, 3, 4), plan?.recentSongIds)
        assertEquals(false, plan?.resetHistory)
    }

    @Test
    fun randomQueueResetsHistoryWhenNotEnoughSongsRemain() {
        val plan = planner.planRandomQueue(
            songs = songs(1, 2, 3, 4),
            recentSongIds = setOf(1, 2),
        )

        assertEquals(listOf(1, 2, 3), plan?.songs?.map { it.id })
        assertEquals(setOf(1, 2, 3), plan?.recentSongIds)
        assertEquals(true, plan?.resetHistory)
    }

    @Test
    fun uniformRandomQueueUsesLowRawPlayCountSongsAfterRecentFilter() {
        val plan = planner.planRandomQueue(
            songs = songs(1, 2, 3, 4, 5),
            recentSongIds = setOf(1),
            uniformRandomEnabled = true,
            playCounts = mapOf(2 to 10, 3 to 0, 4 to 0, 5 to 0),
        )

        assertEquals(listOf(3, 4, 5), plan?.songs?.map { it.id })
    }

    @Test
    fun infiniteStartKeepsCurrentQueueAndRecordsQueuedPlayableSongs() {
        val queue = PlayQueue().setQueue(listOf(song(2), song(9, playable = false)), startIndex = 0)

        val plan = planner.planInfiniteStart(
            allSongs = songs(1, 2, 3, 4),
            queue = queue,
        )

        assertEquals(setOf(2), plan?.playedSongIds)
    }

    @Test
    fun infiniteRefillExcludesSongsAlreadyInQueue() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        val plan = planner.planInfiniteRefill(
            allSongs = songs(1, 2, 3, 4, 5),
            queue = queue,
            playedSongIds = setOf(1, 2),
        )

        assertEquals(listOf(3, 4, 5), plan?.addedSongs?.map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 5), plan?.queue?.songs?.map { it.id })
        assertEquals(setOf(1, 2, 3, 4, 5), plan?.playedSongIds)
    }

    @Test
    fun uniformInfiniteRefillUsesLowRawPlayCountSongsAndKeepsExistingExclusions() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        val plan = planner.planInfiniteRefill(
            allSongs = songs(1, 2, 3, 4, 5, 6),
            queue = queue,
            playedSongIds = setOf(1, 2),
            uniformRandomEnabled = true,
            playCounts = mapOf(3 to 10, 4 to 0, 5 to 0, 6 to 0),
        )

        assertEquals(listOf(4, 5, 6), plan?.addedSongs?.map { it.id })
        assertEquals(listOf(1, 2, 4, 5, 6), plan?.queue?.songs?.map { it.id })
    }

    @Test
    fun ignoresSongsWithoutUri() {
        val plan = planner.planInfiniteStart(
            allSongs = listOf(song(1, playable = false), song(2), song(3)),
            queue = PlayQueue().setQueue(listOf(song(2), song(3)), startIndex = 0),
        )

        assertEquals(setOf(2, 3), plan?.playedSongIds)
    }

    @Test
    fun returnsNullWhenThereAreNoPlayableSongs() {
        val plan = planner.planRandomQueue(
            songs = listOf(song(1, playable = false)),
            recentSongIds = emptySet(),
        )

        assertTrue(plan == null)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int, playable: Boolean = true): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = if (playable) 44_100 else 0,
        )
    }
}
