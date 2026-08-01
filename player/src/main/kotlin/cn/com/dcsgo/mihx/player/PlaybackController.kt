package cn.com.dcsgo.mihx.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.player.mapper.SongMediaItemMapper
import cn.com.dcsgo.mihx.player.service.AppMediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-side transport + state controller (plan P1-5).
 *
 * Connects to the [MediaSession] via a [MediaController] once a token is published by
 * [SessionTokenProvider], registers a [Player.Listener] that converts player events into a
 * [ControllerPlaybackSnapshot] [StateFlow], and exposes play/pause/seek/next/prev/setMediaItems.
 *
 * Connection failures are logged desensitized and expose a recoverable action (call [connect]
 * again once the session is ready) — no crash is thrown to callers.
 */
@Singleton
class PlaybackController @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val tokenProvider: SessionTokenProvider,
    private val mapper: SongMediaItemMapper,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _snapshot = MutableStateFlow(IDLE_SNAPSHOT)
    val snapshot: StateFlow<ControllerPlaybackSnapshot> = _snapshot.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)

    /** Current media-item index inside the transport (window-local). Drives queue highlight. */
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    // Items/seek issued before the MediaController is ready are buffered and flushed on connect
    // (the controller is built asynchronously after the session token is published).
    private var pendingItems: List<MediaItem>? = null
    private var pendingSeekIndex: Int? = null

    private var controller: MediaController? = null
    private var connectJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _currentIndex.value = player.currentMediaItemIndex
            _snapshot.value = _snapshot.value.copy(
                isPlaying = player.isPlaying,
                currentMediaId = player.currentMediaItem?.mediaId,
                positionMs = if (player.currentPosition >= 0) player.currentPosition else 0L,
                durationMs = if (player.duration >= 0) player.duration else 0L,
                playbackState = player.playbackState,
                buffering = player.playbackState == Player.STATE_BUFFERING,
            )
        }
    }

    /** Starts the session service (publishes a token) and attaches a [MediaController]. Idempotent. */
    fun connect() {
        if (connectJob != null) return
        startSessionService()
        connectJob = scope.launch {
            val token = tokenProvider.token.filterNotNull().first()
            buildController(token)
        }
    }

    /**
     * Launches [AppMediaSessionService]. Kept private so the [UnstableApi] opt-in requirement does
     * not leak into [connect]'s public signature — [connect] is consumed by :feature:player and must
     * stay opt-in-free.
     */
    @OptIn(UnstableApi::class)
    private fun startSessionService() {
        context.startForegroundService(Intent(context, AppMediaSessionService::class.java))
    }

    private fun buildController(token: SessionToken) {
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            {
                try {
                    onControllerReady(future.get())
                } catch (e: Exception) {
                    onConnectFailed(e)
                }
            },
            context.mainExecutor,
        )
    }

    private fun onControllerReady(mediaController: MediaController) {
        controller = mediaController
        mediaController.addListener(playerListener)
        _currentIndex.value = mediaController.currentMediaItemIndex
        pendingItems?.let {
            mediaController.setMediaItems(it)
            mediaController.prepare()
        }
        pendingItems = null
        pendingSeekIndex?.let { mediaController.seekTo(it.toLong()) }
        pendingSeekIndex = null
        _snapshot.value = _snapshot.value.copy(
            isPlaying = mediaController.isPlaying,
            currentMediaId = mediaController.currentMediaItem?.mediaId,
            positionMs = if (mediaController.currentPosition >= 0) mediaController.currentPosition else 0L,
            durationMs = if (mediaController.duration >= 0) mediaController.duration else 0L,
            playbackState = mediaController.playbackState,
            buffering = mediaController.playbackState == Player.STATE_BUFFERING,
        )
        AppLogger.d(TAG, "PlaybackController connected to MediaSession.")
    }

    private fun onConnectFailed(e: Exception) {
        AppLogger.w(TAG, "MediaController connect failed (session not ready yet). Retry connect() later.")
    }

    fun currentPosition(): Long = controller?.currentPosition ?: 0L

    fun setMediaItems(items: List<MediaItem>) {
        pendingItems = items
        controller?.setMediaItems(items)
        controller?.prepare()
    }

    /** Maps domain [Song]s to Media3 items and queues them (keeps Media3 types inside the kernel). */
    fun setSongs(songs: List<Song>) {
        setMediaItems(songs.map { mapper.toMediaItem(it) })
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekToNextMediaItem() {
        controller?.seekToNextMediaItem()
    }

    fun seekToPreviousMediaItem() {
        controller?.seekToPreviousMediaItem()
    }

    /** Seeks to a media-item index (window-local). Used by the queue panel "tap to play". */
    fun seekToMediaItem(index: Int) {
        pendingSeekIndex = index
        controller?.seekTo(index.toLong())
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        pendingItems = null
        pendingSeekIndex = null
        connectJob?.cancel()
        connectJob = null
    }

    companion object {
        private const val TAG = "PlaybackController"
        private val IDLE_SNAPSHOT = ControllerPlaybackSnapshot(
            isPlaying = false,
            currentMediaId = null,
            positionMs = 0L,
            durationMs = 0L,
            playbackState = Player.STATE_IDLE,
            buffering = false,
        )
    }
}
