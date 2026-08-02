package cn.com.dcsgo.mihx.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    private var pendingSeekPositionMs: Long? = null
    private var pendingPlay = false

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

    /**
     * Starts the session service (publishes a token) and attaches a [MediaController]. Idempotent.
     *
     * Health-check: the previous guard `if (connectJob != null) return` only protected against
     * double-launching the connect coroutine, but never checked whether the existing
     * [MediaController] was still connected. When the system reclaimed the session service in the
     * background (low-memory or OEM kills) the old controller was released silently; subsequent
     * [play]/[seekTo]/[seekToNextMediaItem] calls became no-ops and the UI looked frozen. Now we
     * reconnect whenever the current controller is gone or disconnected.
     */
    fun connect() {
        if (controller?.isConnected == true) return
        connectJob?.cancel()
        connectJob = null
        AppLogger.d(TAG, "connect(): starting AppMediaSessionService and waiting for token...")
        startSessionService()
        connectJob = scope.launch {
            val token = tokenProvider.token.filterNotNull().first()
            AppLogger.d(TAG, "connect(): token received, building MediaController")
            buildController(token)
        }
    }

    /**
     * Launches [AppMediaSessionService]. Kept private so the unstable-API opt-in requirement does
     * not leak into [connect]'s public signature — [connect] is consumed by :feature:player and must
     * stay opt-in-free. (The project handles Media3 unstable-API opt-in at the lint level via
     * lint.xml, so no Kotlin @OptIn is needed here.)
     */
    private fun startSessionService() {
        // Launch the session service with a plain startService (NOT startForegroundService):
        // Android requires startForeground() within 5s of startForegroundService, but Media3 only
        // promotes the service to foreground once playback actually begins (driven by the
        // MediaNotification.Provider set in AppMediaSessionService). Using startForegroundService
        // here caused ForegroundServiceDidNotStartInTimeException on cold connect. Media3 lifts the
        // service to foreground automatically when the playlist is non-empty (see P3-1).
        context.startService(Intent(context, AppMediaSessionService::class.java))
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
        AppLogger.d(TAG, "onControllerReady: connected, mediaItemCount=${mediaController.mediaItemCount}")
        var hadPendingItems = false
        pendingItems?.let {
            AppLogger.d(TAG, "onControllerReady: flushing ${it.size} pending items")
            mediaController.setMediaItems(it)
            mediaController.prepare()
            hadPendingItems = it.isNotEmpty()
        }
        pendingItems = null
        pendingSeekIndex?.let { mediaController.seekToDefaultPosition(it) }
        pendingSeekIndex = null
        pendingSeekPositionMs?.let { mediaController.seekTo(it) }
        pendingSeekPositionMs = null
        if (pendingPlay) {
            mediaController.play()
            pendingPlay = false
        }
        // 关键修复：如果 flush 了 pending items，重新触发 startService → onStartCommand，
        // 让 Media3 在 player 有内容时创建媒体通知并 startForeground。
        if (hadPendingItems) {
            AppLogger.d(TAG, "onControllerReady: re-triggering startService for notification creation")
            context.startService(Intent(context, AppMediaSessionService::class.java))
        }
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

    /**
     * Resumes at [positionMs] inside the current item. Used by P4-7 restore so the resume position
     * survives a cold-start connect: if the MediaController is not ready yet, the seek is buffered
     * and flushed in [onControllerReady] (after the item-index seek, so the position wins).
     */
    fun resumeFrom(positionMs: Long) {
        pendingSeekPositionMs = positionMs
        controller?.seekTo(positionMs)
    }

    fun setMediaItems(items: List<MediaItem>) {
        pendingItems = items
        controller?.setMediaItems(items)
        controller?.prepare()
        // 关键修复：onStartCommand 在 player 没有 media items 时被调用，Media3 不会创建
        // 通知。设置 media items 后重新触发 startService → onStartCommand，让 Media3 在
        // player 有内容时创建媒体通知并 startForeground（锁屏控件 + 前台保活的前提）。
        if (controller != null && items.isNotEmpty()) {
            AppLogger.d(TAG, "setMediaItems: re-triggering startService for notification creation")
            context.startService(Intent(context, AppMediaSessionService::class.java))
        }
    }

    /** Maps domain [Song]s to Media3 items and queues them (keeps Media3 types inside the kernel). */
    fun setSongs(songs: List<Song>) {
        val items = songs.map { mapper.toMediaItem(it) }
        AppLogger.d(TAG, "setSongs: mapping ${songs.size} songs to MediaItems")
        setMediaItems(items)
    }

    fun play() {
        pendingPlay = true
        controller?.play()
        if (controller != null) pendingPlay = false
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

    /** Appends [items] to the end of the transport queue (window slide, plan P5-C3). */
    fun addMediaItems(items: List<MediaItem>) {
        controller?.addMediaItems(items)
    }

    /** Inserts [items] at transport index [index] (window slide, plan P5-C3). */
    fun addMediaItems(index: Int, items: List<MediaItem>) {
        controller?.addMediaItems(index, items)
    }

    /** Removes the transport items in [fromIndex, toIndex) (window slide, plan P5-C3). */
    fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        controller?.removeMediaItems(fromIndex, toIndex)
    }

    /** Current transport playlist size (window slide, plan P5-C3). */
    fun mediaItemCount(): Int = controller?.mediaItemCount ?: 0

    /**
     * Seeks to a media-item index (window-local). Used by the queue panel "tap to play".
     *
     * IMPORTANT: this is NOT [seekTo] — [androidx.media3.common.Player.seekTo] takes a *position in
     * milliseconds* within the current item, whereas jumping to a different item requires
     * [androidx.media3.common.Player.seekToDefaultPosition]. Passing the item index into `seekTo`
     * silently seeks to `index` ms inside the *current* item (restarting it from ~0), which is the
     * historical "tap a queue song but the current song replays" bug.
     */
    fun seekToMediaItem(index: Int) {
        pendingSeekIndex = index
        controller?.seekToDefaultPosition(index)
    }

    fun release() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        pendingItems = null
        pendingSeekIndex = null
        pendingPlay = false
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
