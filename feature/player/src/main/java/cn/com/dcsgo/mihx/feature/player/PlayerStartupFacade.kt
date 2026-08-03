package cn.com.dcsgo.mihx.feature.player

class PlayerStartupFacade(
    private val startService: () -> Unit,
    private val connectMediaController: () -> Unit,
    private val loadInitialData: (afterInitialSnapshot: () -> Unit) -> Unit,
    private val listenForSongChanges: () -> Unit,
    private val restorePlaybackState: () -> Unit,
) {
    fun start() {
        startService()
        connectMediaController()
        loadInitialData {
            restorePlaybackState()
        }
        listenForSongChanges()
    }
}
