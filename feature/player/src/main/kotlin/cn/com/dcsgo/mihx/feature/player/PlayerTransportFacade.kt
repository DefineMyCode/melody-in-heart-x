package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.player.source.TempMediaStoreSource
import cn.com.dcsgo.mihx.player.PlaybackController
import javax.inject.Inject

/**
 * Thin boundary exposing playback transport to the ViewModel (plan P1-5 / P1-9).
 *
 * Owns the connect-then-load sequence: starts the session service (so a token is published) and
 * attaches the [PlaybackController], then queues the temp MediaStore library.
 */
class PlayerTransportFacade @Inject constructor(
    private val controller: PlaybackController,
    private val source: TempMediaStoreSource,
) : PlayerFacade {

    /** Starts the session service (publishes a token) and attaches the controller. */
    fun connect() = controller.connect()

    /** Loads the temp MediaStore library and queues it on the controller. */
    fun loadTempLibrary() {
        val songs: List<Song> = source.loadSongs()
        controller.setSongs(songs)
    }

    fun play() = controller.play()
    fun pause() = controller.pause()
    fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    fun seekToNext() = controller.seekToNextMediaItem()
    fun seekToPrevious() = controller.seekToPreviousMediaItem()
}
