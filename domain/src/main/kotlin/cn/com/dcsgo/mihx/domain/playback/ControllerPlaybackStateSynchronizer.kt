package cn.com.dcsgo.mihx.domain.playback

/**
 * THE ONLY place where a [ControllerPlaybackSnapshot] is mapped into a
 * [PlayerUiState] (architecture gate A4). Duration is only accepted when >= 0.
 */
object ControllerPlaybackStateSynchronizer {

    fun synchronize(snapshot: ControllerPlaybackSnapshot): PlayerUiState {
        val safeDuration = if (snapshot.durationMs >= 0) snapshot.durationMs else 0L
        val safePosition = if (snapshot.positionMs >= 0) snapshot.positionMs else 0L
        val state = when {
            snapshot.buffering -> PlaybackState.BUFFERING
            snapshot.playbackState < 0 -> PlaybackState.ERROR
            snapshot.isPlaying -> PlaybackState.PLAYING
            else -> PlaybackState.PAUSED
        }
        return PlayerUiState(
            playbackState = state,
            isPlaying = snapshot.isPlaying,
            currentMediaId = snapshot.currentMediaId,
            positionMs = safePosition,
            durationMs = safeDuration,
        )
    }
}
