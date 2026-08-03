package cn.com.dcsgo.mihx.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PlayerLifecycleFacadeTest {

    private val calls = mutableListOf<String>()
    private val infoLogs = mutableListOf<String>()
    private val errorLogs = mutableListOf<Pair<String, Throwable>>()
    private var startError: RuntimeException? = null
    private val facade = PlayerLifecycleFacade(
        startMediaSessionService = {
            calls += "startService"
            startError?.let { throw it }
        },
        syncControllerPlaybackState = { calls += "sync" },
        savePlaybackState = { calls += "save" },
        releasePlaybackController = { calls += "releasePlaybackController" },
        releaseBluetoothPlayback = { calls += "releaseBluetoothPlayback" },
        releasePlayDurationTracker = { calls += "releasePlayDurationTracker" },
        logInfo = { infoLogs += it },
        logError = { message, error -> errorLogs += message to error },
    )

    @Test
    fun startServiceLogsSuccessWhenStartSucceeds() {
        facade.startService()

        assertEquals(listOf("startService"), calls)
        assertEquals(listOf("AppMediaSessionService start requested"), infoLogs)
        assertEquals(emptyList<Pair<String, Throwable>>(), errorLogs)
    }

    @Test
    fun startServiceLogsFailureWhenStartThrows() {
        val error = RuntimeException("boom")
        startError = error

        facade.startService()

        assertEquals(listOf("startService"), calls)
        assertEquals(emptyList<String>(), infoLogs)
        assertEquals("Failed to start AppMediaSessionService", errorLogs.single().first)
        assertSame(error, errorLogs.single().second)
    }

    @Test
    fun onClearedSavesBeforeReleasingResources() {
        facade.onCleared()

        assertEquals(
            listOf(
                "sync",
                "save",
                "releasePlaybackController",
                "releaseBluetoothPlayback",
                "releasePlayDurationTracker",
            ),
            calls,
        )
        assertEquals(listOf("onCleared"), infoLogs)
    }
}
