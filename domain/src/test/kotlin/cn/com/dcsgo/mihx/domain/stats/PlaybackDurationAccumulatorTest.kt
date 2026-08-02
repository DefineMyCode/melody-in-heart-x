package cn.com.dcsgo.mihx.domain.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackDurationAccumulatorTest {

    private fun accumulator() = PlaybackDurationAccumulator(shortPlayThresholdMs = 30_000L)

    @Test
    fun `accumulates only while playing`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)
        acc.onPlayingChanged(false, nowMs = 10_000L) // played 10s
        acc.onPlayingChanged(true, nowMs = 50_000L) // paused 40s, not counted
        val result = acc.onSongChanged(2L, nowMs = 60_000L, previousCompleted = false)

        assertEquals(1L, result?.songId)
        assertEquals(20_000L, result?.playedMs)
    }

    @Test
    fun `natural completion is not a skip`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)
        val result = acc.onSongChanged(2L, nowMs = 5_000L, previousCompleted = true)

        assertTrue(result!!.completed)
        assertFalse(result.shortPlay, "a completed item is never a short play, even below threshold")
    }

    @Test
    fun `skipping below threshold is a short play`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)
        val result = acc.onSongChanged(2L, nowMs = 8_000L, previousCompleted = false)

        assertEquals(8_000L, result?.playedMs)
        assertFalse(result!!.completed)
        assertTrue(result.shortPlay)
    }

    @Test
    fun `skipping above threshold is a skip but not a short play`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)
        val result = acc.onSongChanged(2L, nowMs = 45_000L, previousCompleted = false)

        assertFalse(result!!.completed)
        assertFalse(result.shortPlay)
    }

    @Test
    fun `no session is emitted when nothing was played`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        // user skips immediately without ever starting playback
        assertNull(acc.onSongChanged(2L, nowMs = 0L, previousCompleted = false))
    }

    @Test
    fun `first transition has no previous session`() {
        val acc = accumulator()
        assertNull(acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false))
    }

    @Test
    fun `playback continues across a transition`() {
        val acc = accumulator()
        acc.onPlayingChanged(true, nowMs = 0L)
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        // transition happens while still playing; the new song must start accruing right away
        acc.onSongChanged(2L, nowMs = 10_000L, previousCompleted = true)
        val second = acc.finish(nowMs = 25_000L, completed = true)

        assertEquals(2L, second?.songId)
        assertEquals(15_000L, second?.playedMs)
    }

    @Test
    fun `finish settles the in-flight session and clears state`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)

        val finished = acc.finish(nowMs = 12_000L, completed = false)
        assertEquals(12_000L, finished?.playedMs)
        assertTrue(finished!!.shortPlay)

        // a second finish must not double-count
        assertNull(acc.finish(nowMs = 20_000L, completed = false))
    }

    @Test
    fun `repeated identical playing states are ignored`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        acc.onPlayingChanged(true, nowMs = 0L)
        acc.onPlayingChanged(true, nowMs = 5_000L) // duplicate resume must not reset the clock
        val result = acc.finish(nowMs = 10_000L, completed = false)

        assertEquals(10_000L, result?.playedMs)
    }

    @Test
    fun `completed item with zero playtime is still recorded`() {
        val acc = accumulator()
        acc.onSongChanged(1L, nowMs = 0L, previousCompleted = false)
        val result = acc.onSongChanged(2L, nowMs = 0L, previousCompleted = true)

        assertEquals(0L, result?.playedMs)
        assertTrue(result!!.completed)
    }
}
