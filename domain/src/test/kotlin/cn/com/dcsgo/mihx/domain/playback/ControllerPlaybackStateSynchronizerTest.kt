package cn.com.dcsgo.mihx.domain.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers every mapping branch of [ControllerPlaybackStateSynchronizer] (architecture gate A4 is
 * the single construction point of [PlayerUiState], plan P6-1).
 */
class ControllerPlaybackStateSynchronizerTest {

    private fun snapshot(
        isPlaying: Boolean = false,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playbackState: Int = 0,
        buffering: Boolean = false,
        currentMediaId: String? = "1",
    ) = ControllerPlaybackSnapshot(
        isPlaying = isPlaying,
        currentMediaId = currentMediaId,
        positionMs = positionMs,
        durationMs = durationMs,
        playbackState = playbackState,
        buffering = buffering,
    )

    @Test
    fun `buffering wins over every other signal`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(buffering = true, isPlaying = true))
        assertEquals(PlaybackState.BUFFERING, state.playbackState)
    }

    @Test
    fun `negative playbackState maps to error`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(playbackState = -1))
        assertEquals(PlaybackState.ERROR, state.playbackState)
    }

    @Test
    fun `playing maps to playing`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(isPlaying = true))
        assertEquals(PlaybackState.PLAYING, state.playbackState)
        assertEquals(true, state.isPlaying)
    }

    @Test
    fun `idle not playing maps to paused`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot())
        assertEquals(PlaybackState.PAUSED, state.playbackState)
        assertEquals(false, state.isPlaying)
    }

    @Test
    fun `ended state with playing false maps to paused`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(
            snapshot(playbackState = 4, isPlaying = false),
        )
        assertEquals(PlaybackState.PAUSED, state.playbackState)
    }

    @Test
    fun `negative duration is clamped to zero`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(durationMs = -100L))
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `negative position is clamped to zero`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(positionMs = -5L))
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `non-negative position and duration pass through`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(
            snapshot(positionMs = 12_345L, durationMs = 234_000L),
        )
        assertEquals(12_345L, state.positionMs)
        assertEquals(234_000L, state.durationMs)
    }

    @Test
    fun `current media id passes through`() {
        val state = ControllerPlaybackStateSynchronizer.synchronize(snapshot(currentMediaId = "42"))
        assertEquals("42", state.currentMediaId)
        val empty = ControllerPlaybackStateSynchronizer.synchronize(snapshot(currentMediaId = null))
        assertEquals(null, empty.currentMediaId)
    }
}
