package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackBridgeFacadeTest {

    private val calls = mutableListOf<String>()
    private val queue = PlayQueue().setQueue(listOf(song(1)), startIndex = 0)
    private val facade = PlayerPlaybackBridgeFacade(
        remainingMediaItems = {
            calls += "remaining"
            3
        },
        pausePlayback = { calls += "pause" },
        resumePlayback = { calls += "resume" },
        currentPlaybackPositionMs = {
            calls += "position"
            42L
        },
        clearControllerPlaylist = {
            calls += "clearController"
            true
        },
        startControllerQueuePlayback = { _, index ->
            calls += "startQueue:$index"
            true
        },
        prepareControllerQueue = { _, index, position ->
            calls += "prepareQueue:$index:$position"
            true
        },
        startControllerSinglePlayback = { song ->
            calls += "startSingle:${song.id}"
            true
        },
        playFromQueue = { _, index -> calls += "playFromQueue:$index" },
        syncPlayerQueue = { calls += "syncQueue" },
        refillInfinitePlayQueue = { startedSongId, advanceAfterWrap ->
            calls += "refill:$startedSongId:$advanceAfterWrap"
        },
    )

    @Test
    fun delegatesPlaybackBridgeCalls() {
        assertEquals(3, facade.remainingMediaItems())
        facade.pausePlayback()
        facade.resumePlayback()
        assertEquals(42L, facade.currentPlaybackPositionMs())
        assertTrue(facade.clearControllerPlaylist())
        assertTrue(facade.startControllerQueuePlayback(queue, 0))
        assertTrue(facade.prepareControllerQueue(queue, 0, 99L))
        assertTrue(facade.startControllerSinglePlayback(song(2)))
        facade.playFromQueue(queue, 0)
        facade.syncPlayerQueue(queue)
        facade.refillInfinitePlayQueue(2, true)

        assertEquals(
            listOf(
                "remaining",
                "pause",
                "resume",
                "position",
                "clearController",
                "startQueue:0",
                "prepareQueue:0:99",
                "startSingle:2",
                "playFromQueue:0",
                "syncQueue",
                "refill:2:true",
            ),
            calls,
        )
    }

    private fun song(id: Int): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
