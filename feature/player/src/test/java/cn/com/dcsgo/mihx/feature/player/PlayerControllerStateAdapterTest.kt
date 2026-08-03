package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerControllerStateAdapterTest {

    private var snapshot: ControllerPlaybackSnapshot? = null
    private var isPlayingChange: Pair<Boolean, Boolean>? = null
    private val adapter = PlayerControllerStateAdapter(
        syncControllerPlaybackState = { snapshot = it },
        handleControllerIsPlayingChanged = { isPlaying, isBuffering ->
            isPlayingChange = isPlaying to isBuffering
        },
    )

    @Test
    fun syncForwardsPlaybackSnapshot() {
        val incoming = ControllerPlaybackSnapshot(
            mediaId = "42",
            isPlaying = true,
            isBuffering = false,
            currentPositionMs = 500L,
            durationMs = 1_000L,
        )

        adapter.sync(incoming)

        assertEquals(incoming, snapshot)
    }

    @Test
    fun handleIsPlayingChangedForwardsBufferingState() {
        adapter.handleIsPlayingChanged(
            isPlaying = false,
            isBuffering = true,
        )

        assertEquals(false to true, isPlayingChange)
    }
}
