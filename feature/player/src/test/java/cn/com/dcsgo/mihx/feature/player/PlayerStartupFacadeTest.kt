package cn.com.dcsgo.mihx.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStartupFacadeTest {

    private val calls = mutableListOf<String>()
    private val facade = PlayerStartupFacade(
        startService = { calls += "startService" },
        connectMediaController = { calls += "connectMediaController" },
        loadInitialData = { afterInitialSnapshot ->
            calls += "loadInitialData:start"
            afterInitialSnapshot()
            calls += "loadInitialData:end"
        },
        listenForSongChanges = { calls += "listenForSongChanges" },
        restorePlaybackState = { calls += "restorePlaybackState" },
    )

    @Test
    fun startRunsStartupStepsInOrderAndRestoresAfterInitialSnapshot() {
        facade.start()

        assertEquals(
            listOf(
                "startService",
                "connectMediaController",
                "loadInitialData:start",
                "restorePlaybackState",
                "loadInitialData:end",
                "listenForSongChanges",
            ),
            calls,
        )
    }
}
