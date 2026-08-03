package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControllerQueueFacadeTest {

    private var state = PlayerUiState()
    private var controllerQueueInfo: ControllerQueueInfo? = null
    private var cleared = false
    private var syncedPlan: ControllerQueuePlan? = null
    private var syncCount = 0
    private val facade = PlayerControllerQueueFacade(
        state = { state },
        controllerQueueInfo = { controllerQueueInfo },
        clearPlaylist = { cleared = true },
        syncQueue = {
            syncCount += 1
            syncedPlan = it
        },
        controllerQueuePlanner = object : ControllerQueuePlannerPort {
            override fun plan(queue: PlayQueue, requestedIndex: Int): ControllerQueuePlan? {
                if (queue.isEmpty || requestedIndex !in queue.songs.indices) return null
                return ControllerQueuePlan(queue.songs, requestedIndex)
            }
        },
    )

    @Test
    fun remainingMediaItemsUsesControllerInfoWhenAvailable() {
        state = state.copy(playQueue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0))
        controllerQueueInfo = ControllerQueueInfo(mediaItemCount = 5, currentMediaItemIndex = 1)

        assertEquals(3, facade.remainingMediaItems())
    }

    @Test
    fun remainingMediaItemsFallsBackToPlannedQueue() {
        state = state.copy(playQueue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1))

        assertEquals(1, facade.remainingMediaItems())
    }

    @Test
    fun clearControllerPlaylistDelegatesAndReturnsTrue() {
        assertTrue(facade.clearControllerPlaylist())
        assertTrue(cleared)
    }

    @Test
    fun syncPlayerQueueBuildsPlanAndDelegates() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 1)

        facade.syncPlayerQueue(queue)

        assertEquals(listOf(1, 2), syncedPlan?.songs?.map { it.id })
        assertEquals(1, syncedPlan?.startIndex)
    }

    @Test
    fun syncPlayerQueueSkipsDuplicatePlan() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        facade.syncPlayerQueue(queue)
        facade.syncPlayerQueue(queue)

        assertEquals(1, syncCount)
    }

    @Test
    fun clearControllerPlaylistAllowsSamePlanToSyncAgain() {
        val queue = PlayQueue().setQueue(songs(1), startIndex = 0)

        facade.syncPlayerQueue(queue)
        facade.clearControllerPlaylist()
        facade.syncPlayerQueue(queue)

        assertEquals(2, syncCount)
    }

    @Test
    fun syncPlayerQueueIgnoresInvalidQueue() {
        val queue = PlayQueue(songs = songs(1), currentIndex = 5)

        facade.syncPlayerQueue(queue)

        assertEquals(null, syncedPlan)
    }

    @Test
    fun buildControllerQueueReturnsNullForEmptyQueue() {
        assertEquals(null, facade.buildControllerQueue(PlayQueue()))
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
