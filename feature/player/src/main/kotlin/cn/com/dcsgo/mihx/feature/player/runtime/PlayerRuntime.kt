package cn.com.dcsgo.mihx.feature.player.runtime

import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.feature.player.PlayerTransportFacade
import cn.com.dcsgo.mihx.player.PlaybackController
import cn.com.dcsgo.mihx.player.PlayerPlaybackProgressTicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Hosts the playback logic for the 播放 screen (plan P1-9): connects the controller, loads the
 * temp library, and exposes the snapshot plus a 500ms progress flow to the ViewModel.
 */
class PlayerRuntime @Inject constructor(
    private val facade: PlayerTransportFacade,
    private val controller: PlaybackController,
    private val ticker: PlayerPlaybackProgressTicker,
) {

    val snapshot: StateFlow<ControllerPlaybackSnapshot> = controller.snapshot

    fun start() {
        facade.connect()
        facade.loadTempLibrary()
    }

    /** Emits the current position every 500ms while playing. */
    fun progressFlow(): Flow<Long> =
        ticker.progress(controller.snapshot.map { it.isPlaying }) { controller.currentPosition() }

    fun play() = facade.play()
    fun pause() = facade.pause()
    fun seekTo(positionMs: Long) = facade.seekTo(positionMs)
    fun seekToNext() = facade.seekToNext()
    fun seekToPrevious() = facade.seekToPrevious()
}
