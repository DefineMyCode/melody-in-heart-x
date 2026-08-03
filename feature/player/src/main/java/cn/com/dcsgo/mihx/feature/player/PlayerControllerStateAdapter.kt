package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot

class PlayerControllerStateAdapter(
    private val syncControllerPlaybackState: (ControllerPlaybackSnapshot) -> Unit,
    private val handleControllerIsPlayingChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
) {
    fun sync(snapshot: ControllerPlaybackSnapshot) {
        syncControllerPlaybackState(snapshot)
    }

    fun handleIsPlayingChanged(isPlaying: Boolean, isBuffering: Boolean) {
        handleControllerIsPlayingChanged(
            isPlaying,
            isBuffering,
        )
    }
}
