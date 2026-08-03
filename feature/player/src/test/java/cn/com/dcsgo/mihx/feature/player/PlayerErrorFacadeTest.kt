package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerErrorFacadeTest {

    private var state = PlayerUiState()
    private var startedIndex: Int? = null
    private val facade = PlayerErrorFacade(
        updateState = { transform -> state = transform(state) },
        startQueuePlayback = { _, index ->
            startedIndex = index
            true
        },
        isPlayable = { it.sampleRate > 0 },
    )

    @Test
    fun clearErrorRemovesCurrentErrorMessage() {
        state = state.copy(errorMessage = "old error")

        facade.clearError()

        assertEquals(null, state.errorMessage)
    }

    @Test
    fun playFromQueueStartsPlaybackForPlayableSong() {
        val queue = PlayQueue().setQueue(listOf(song(1, sampleRate = 44_100)), startIndex = 0)

        facade.playFromQueue(queue, index = 0)

        assertEquals(0, startedIndex)
        assertEquals(null, state.errorMessage)
    }

    @Test
    fun playFromQueueShowsErrorForSongWithoutLocalFile() {
        val queue = PlayQueue().setQueue(listOf(song(1, title = "Missing", sampleRate = 0)), startIndex = 0)

        facade.playFromQueue(queue, index = 0)

        assertEquals(null, startedIndex)
        assertEquals("Missing has no local file", state.errorMessage)
    }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = sampleRate,
        )
    }
}
