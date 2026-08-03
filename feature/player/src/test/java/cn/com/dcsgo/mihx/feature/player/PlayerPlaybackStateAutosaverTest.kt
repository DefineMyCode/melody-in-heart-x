package cn.com.dcsgo.mihx.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaybackStateAutosaverTest {

    private var nowMs = 0L
    private val calls = mutableListOf<String>()
    private val autosaver = PlayerPlaybackStateAutosaver(
        currentTimeMs = { nowMs },
        syncPlaybackState = { calls += "sync" },
        savePlaybackState = { positionMs -> calls += "save:$positionMs" },
        intervalMs = 1_000L,
    )

    @Test
    fun onPlaybackPositionSavesImmediatelyThenThrottles() {
        autosaver.onPlaybackPosition(100L)
        nowMs = 500L
        autosaver.onPlaybackPosition(600L)
        nowMs = 1_000L
        autosaver.onPlaybackPosition(1_100L)

        assertEquals(
            listOf(
                "sync",
                "save:100",
                "sync",
                "save:1100",
            ),
            calls,
        )
    }

    @Test
    fun resetAllowsNextPositionToSaveImmediately() {
        autosaver.onPlaybackPosition(100L)
        nowMs = 500L
        autosaver.reset()
        autosaver.onPlaybackPosition(600L)

        assertEquals(
            listOf(
                "sync",
                "save:100",
                "sync",
                "save:600",
            ),
            calls,
        )
    }
}
