package cn.com.dcsgo.mihx.feature.player

class PlayerPlaybackStateAutosaver(
    private val currentTimeMs: () -> Long,
    private val syncPlaybackState: () -> Unit,
    private val savePlaybackState: (Long) -> Unit,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private var lastSaveTimeMs: Long? = null

    fun onPlaybackPosition(positionMs: Long) {
        val now = currentTimeMs()
        val lastSave = lastSaveTimeMs
        if (lastSave != null && now - lastSave < intervalMs) return

        lastSaveTimeMs = now
        syncPlaybackState()
        savePlaybackState(positionMs)
    }

    fun reset() {
        lastSaveTimeMs = null
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 1_000L
    }
}
