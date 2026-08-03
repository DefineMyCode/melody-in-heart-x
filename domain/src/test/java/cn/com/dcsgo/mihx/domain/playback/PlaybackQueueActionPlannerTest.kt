package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueActionPlannerTest {

    private val planner = PlaybackQueueActionPlanner()

    @Test
    fun replaceQueueStartsAtRequestedIndexAndExitsInfinitePlay() {
        val plan = planner.replaceQueue(
            currentQueue = PlayQueue().setQueue(songs(9), startIndex = 0, mode = PlayMode.SHUFFLE),
            songs = songs(1, 2, 3),
            startIndex = 1,
            mode = null,
        )

        assertEquals(listOf(1, 2, 3), plan?.queue?.songs?.map { it.id })
        assertEquals(1, plan?.queue?.currentIndex)
        assertEquals(PlayMode.SHUFFLE, plan?.queue?.playMode)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.PlayQueueIndex(1), plan?.playbackAction)
        assertTrue(plan?.exitInfinitePlay == true)
    }

    @Test
    fun addSongToEmptyQueueStartsPlayback() {
        val plan = planner.addSong(PlayQueue(), song(1))

        assertEquals(listOf(1), plan?.queue?.songs?.map { it.id })
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.PlayQueueIndex(0), plan?.playbackAction)
        assertEquals(1, plan?.addedCount)
    }

    @Test
    fun addDuplicateSongReturnsNull() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        val plan = planner.addSong(queue, song(1))

        assertEquals(null, plan)
    }

    @Test
    fun addSongsAllowsDuplicateSongsAtQueueTail() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)

        val plan = planner.addSongs(queue, songs(2, 3))

        assertEquals(listOf(1, 2, 2, 3), plan?.queue?.songs?.map { it.id })
        assertEquals(listOf(1, 2, 2, 3), plan?.queue?.currentPlayOrderIds())
        assertEquals(2, plan?.addedCount)
    }

    @Test
    fun duplicateSongsKeepAllOccurrencesInReverseAndShuffleOrder() {
        val sequential = PlayQueue().setQueue(songs(1, 2, 2, 3), startIndex = 1)
        val reverse = sequential.setPlayMode(PlayMode.REVERSE)
        val shuffle = sequential.setPlayMode(PlayMode.SHUFFLE)

        assertEquals(listOf(3, 2, 2, 1), reverse.currentPlayOrderIds())
        assertEquals(4, shuffle.currentPlayOrderIds().size)
        assertEquals(2, shuffle.currentPlayOrderIds().count { it == 2 })
        assertEquals(1, shuffle.currentPlayOrderIndices().first())
    }

    @Test
    fun addSongAsNextStoresRestoreModeAndSkipsRefillWhenInfinite() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0, mode = PlayMode.SHUFFLE)

        val plan = planner.addSongAsNext(
            queue = queue,
            song = song(3),
            playModeBeforeNext = null,
            isInfinitePlay = true,
        )

        assertEquals(listOf(1, 3, 2), plan.queue.songs.map { it.id })
        assertEquals(PlayMode.SEQUENTIAL, plan.queue.playMode)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.SyncQueue, plan.playbackAction)
        assertEquals(3, plan.nextPlayState?.songId)
        assertEquals(PlayMode.SHUFFLE, plan.nextPlayState?.playModeBeforeNext)
        assertTrue(plan.nextPlayState?.skipNextRefill == true)
    }

    @Test
    fun addSongsAsNextMovesExistingSongsAndDeduplicatesInput() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3, 4), startIndex = 1, mode = PlayMode.SHUFFLE)

        val plan = planner.addSongsAsNext(
            queue = queue,
            songs = songs(4, 3, 4, 5),
            playModeBeforeNext = null,
            isInfinitePlay = false,
        )

        assertEquals(listOf(1, 2, 4, 3, 5), plan?.queue?.songs?.map { it.id })
        assertEquals(PlayMode.SEQUENTIAL, plan?.queue?.playMode)
        assertEquals(3, plan?.addedCount)
        assertEquals(4, plan?.nextPlayState?.songId)
        assertEquals(PlayMode.SHUFFLE, plan?.nextPlayState?.playModeBeforeNext)
    }

    @Test
    fun addSongsAsNextKeepsCurrentSongAsAnchorWhenInputContainsIt() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1)

        val plan = planner.addSongsAsNext(
            queue = queue,
            songs = songs(2, 3, 4),
            playModeBeforeNext = null,
            isInfinitePlay = false,
        )

        assertEquals(listOf(1, 2, 3, 4), plan?.queue?.songs?.map { it.id })
        assertEquals(1, plan?.queue?.currentIndex)
        assertEquals(2, plan?.addedCount)
    }

    @Test
    fun addSongsAsNextOnEmptyQueueStartsPlayback() {
        val plan = planner.addSongsAsNext(
            queue = PlayQueue(),
            songs = songs(1, 2, 1),
            playModeBeforeNext = null,
            isInfinitePlay = false,
        )

        assertEquals(listOf(1, 2), plan?.queue?.songs?.map { it.id })
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.PlayQueueIndex(0), plan?.playbackAction)
        assertEquals(2, plan?.addedCount)
    }

    @Test
    fun removeCurrentSongFromQueueStartsAdjustedCurrentSong() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1)

        val plan = planner.removeSong(queue, songId = 2)

        assertEquals(listOf(1, 3), plan.queue.songs.map { it.id })
        assertEquals(1, plan.queue.currentIndex)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.PlayQueueIndex(1), plan.playbackAction)
        assertFalse(plan.clearCurrentSong)
    }

    @Test
    fun removeOnlyCurrentSongClearsControllerAndCurrentSong() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        val plan = planner.removeSong(queue, songId = 1)

        assertTrue(plan.queue.isEmpty)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.ClearController, plan.playbackAction)
        assertTrue(plan.clearCurrentSong)
    }

    @Test
    fun removeSongAtRemovesOnlyRequestedDuplicateQueueItem() {
        val queue = PlayQueue().setQueue(songs(1, 2, 2, 3), startIndex = 2)

        val plan = planner.removeSongAt(queue, index = 1)

        assertEquals(listOf(1, 2, 3), plan.queue.songs.map { it.id })
        assertEquals(1, plan.queue.currentIndex)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.SyncQueue, plan.playbackAction)
    }

    @Test
    fun clearQueueClearsControllerAndPersistence() {
        val plan = planner.clearQueue()

        assertTrue(plan.queue.isEmpty)
        assertEquals(PlaybackQueueActionPlanner.PlaybackAction.ClearController, plan.playbackAction)
        assertFalse(plan.savePlaybackState)
        assertTrue(plan.clearPlaybackState)
        assertTrue(plan.clearCurrentSong)
        assertTrue(plan.clearDuration)
    }

    @Test
    fun playQueueItemRejectsInvalidIndex() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        val plan = planner.playQueueItem(queue, index = 2)

        assertEquals(null, plan)
    }

    @Test
    fun setShuffleModeUsesInjectedPlayOrderBuilder() {
        val planner = PlaybackQueueActionPlanner(
            QueueManager.PlayOrderBuilder { songs, _, mode ->
                if (mode == PlayMode.SHUFFLE) {
                    listOf(2, 1, 3)
                } else {
                    PlayQueue.buildPlayOrderIds(songs, 0, mode)
                }
            }
        )
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0)

        val plan = planner.setPlayMode(queue, PlayMode.SHUFFLE)

        assertEquals(listOf(2, 1, 3), plan.queue.currentPlayOrderIds())
    }

    @Test
    fun playQueueItemUsesInjectedPlayOrderBuilderWhenShuffleIsActive() {
        val planner = PlaybackQueueActionPlanner(
            QueueManager.PlayOrderBuilder { songs, startIndex, mode ->
                if (mode == PlayMode.SHUFFLE) {
                    listOf(songs[startIndex].id, 3, 1)
                } else {
                    PlayQueue.buildPlayOrderIds(songs, startIndex, mode)
                }
            }
        )
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0, mode = PlayMode.SHUFFLE)

        val plan = planner.playQueueItem(queue, index = 1)

        assertEquals(listOf(2, 3, 1), plan?.queue?.currentPlayOrderIds())
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
