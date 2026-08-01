package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.player.PlaybackController
import javax.inject.Inject

/**
 * Thin boundary exposing playback transport to the ViewModel (plan P1-5 / P1-9).
 *
 * Owns the connect sequence: starts the session service (so a token is published) and attaches the
 * [PlaybackController]. Loading the library is routed through [cn.com.dcsgo.mihx.feature.player.PlayerQueueFacade]
 * (in [cn.com.dcsgo.mihx.feature.player.runtime.PlayerRuntime]) so the windowed queue is the single
 * source of truth.
 */
class PlayerTransportFacade @Inject constructor(
    private val controller: PlaybackController,
) : PlayerFacade {

    /** Starts the session service (publishes a token) and attaches the controller. */
    fun connect() = controller.connect()

    fun play() = controller.play()
    fun pause() = controller.pause()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun seekToNext() = controller.seekToNextMediaItem()
    fun seekToPrevious() = controller.seekToPreviousMediaItem()
}
