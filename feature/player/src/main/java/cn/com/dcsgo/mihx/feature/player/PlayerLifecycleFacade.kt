package cn.com.dcsgo.mihx.feature.player

class PlayerLifecycleFacade(
    private val startMediaSessionService: () -> Unit,
    private val syncControllerPlaybackState: () -> Unit = {},
    private val savePlaybackState: () -> Unit,
    private val releasePlaybackController: () -> Unit,
    private val releaseBluetoothPlayback: () -> Unit,
    private val releasePlayDurationTracker: () -> Unit,
    private val stopPlaybackProgressTicker: () -> Unit = {},
    private val logInfo: (String) -> Unit,
    private val logError: (String, Throwable) -> Unit,
) {
    fun startService() {
        try {
            startMediaSessionService()
            logInfo("AppMediaSessionService start requested")
        } catch (e: Exception) {
            logError("Failed to start AppMediaSessionService", e)
        }
    }

    fun onCleared() {
        logInfo("onCleared")
        syncControllerPlaybackState()
        savePlaybackState()
        releasePlaybackController()
        releaseBluetoothPlayback()
        releasePlayDurationTracker()
        stopPlaybackProgressTicker()
    }
}
