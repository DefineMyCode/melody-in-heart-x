package cn.com.dcsgo.mihx.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaybackProgressTickerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var playing = false
    private var position = 0L
    private val updates = mutableListOf<Long>()
    private val ticker = PlayerPlaybackProgressTicker(
        scope = scope,
        isPlaying = { playing },
        currentPositionMs = { position },
        updatePosition = { updates += it },
        intervalMs = 60_000L,
    )

    @Test
    fun updateRunningStatePublishesCurrentPositionWhenPlaybackStarts() {
        playing = true
        position = 123L

        ticker.updateRunningState()

        assertEquals(listOf(123L), updates)
        ticker.stop()
    }

    @Test
    fun updateRunningStateDoesNothingWhenPlaybackIsPaused() {
        playing = false
        position = 123L

        ticker.updateRunningState()

        assertEquals(emptyList<Long>(), updates)
    }
}
