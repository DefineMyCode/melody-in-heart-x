package cn.com.dcsgo.mihx.feature.player.runtime

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.domain.queue.QueueRestore
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.feature.player.PlayerQueueFacade
import cn.com.dcsgo.mihx.feature.player.PlayerTransportFacade
import cn.com.dcsgo.mihx.player.PlaybackController
import cn.com.dcsgo.mihx.player.PlayerPlaybackProgressTicker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Hosts the playback logic for the 播放 screen (plan P1-9 / P2-9): connects the controller, loads
 * the library, and exposes the snapshot plus a 500ms progress flow to the ViewModel. Also
 * surfaces the queue contract ([PlayerQueueFacade]) so the queue panel can display, highlight,
 * reorder and jump — all in domain types, no Media3 leakage.
 */
class PlayerRuntime @Inject constructor(
    private val facade: PlayerTransportFacade,
    private val queueFacade: PlayerQueueFacade,
    private val songRepository: SongRepository,
    private val controller: PlaybackController,
    private val ticker: PlayerPlaybackProgressTicker,
    private val playbackStateRepository: PlaybackStateRepository,
) {

    val snapshot: StateFlow<ControllerPlaybackSnapshot> = controller.snapshot
    val queue: StateFlow<PlayQueue> = queueFacade.queue
    val currentQueueIndex: StateFlow<Int> = queueFacade.currentQueueIndex

    /** Once a snapshot has been restored we never restore again within this process lifetime. */
    private var restored = false

    fun start() {
        facade.connect()
    }

    /**
     * Loads the library from Room into the queue. The library now comes from the SAF import
     * pipeline (plan P5-A) persisted in Room, so no [android.Manifest.permission.READ_MEDIA_AUDIO]
     * gate is required here. If a persisted snapshot exists it is restored (queue order / mode /
     * current item / position) in a paused state (plan P4-6/7).
     *
     * Idempotent bootstrap: if a queue is already set (playlist/home tap-to-play, a restored
     * snapshot, or a previous load) the transport is left untouched. The player screen re-runs
     * this from [androidx.compose.runtime.LaunchedEffect] on every nav re-entry, and without this
     * guard the whole library would replace the current queue and restart from its first song.
     */
    suspend fun loadLibrary() {
        if (queueFacade.queue.value.songs.isNotEmpty()) return
        val library = songRepository.getAll()
        // Empty library: nothing to play. Do not push an empty queue into the window planner /
        // transport (it would plan a window with currentIndex = -1 and crash the seek).
        if (library.isEmpty()) return
        val snapshot = playbackStateRepository.loadSnapshot()
        if (snapshot != null && !restored) {
            restored = true
            queueFacade.setQueue(QueueRestore.restore(library, snapshot))
            controller.resumeFrom(snapshot.positionMs)
        } else {
            queueFacade.setQueue(library)
        }
    }

    /** Emits the current position every 500ms while playing. */
    fun progressFlow(): Flow<Long> =
        ticker.progress(controller.snapshot.map { it.isPlaying }) { controller.currentPosition() }

    fun play() = facade.play()
    fun pause() = facade.pause()
    fun seekTo(positionMs: Long) = facade.seekTo(positionMs)
    fun seekToNext() = facade.seekToNext()
    fun seekToPrevious() = facade.seekToPrevious()

    // Queue contract (plan P2-8 / P2-9).
    fun jumpTo(index: Int) = queueFacade.jumpTo(index)
    fun switchPlayMode(mode: PlayMode) = queueFacade.switchPlayMode(mode)
    fun removeAt(queueIndex: Int) = queueFacade.removeAt(queueIndex)
    fun addSongAsNext(songs: List<Song>) = queueFacade.addSongAsNext(songs)
    fun addSongsToTail(songs: List<Song>, allowDuplicates: Boolean) =
        queueFacade.addSongsToTail(songs, allowDuplicates)
}
