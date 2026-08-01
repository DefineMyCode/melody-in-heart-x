package cn.com.dcsgo.mihx.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.player.PlaybackStateBuffer
import cn.com.dcsgo.mihx.player.PlayerFactory
import cn.com.dcsgo.mihx.player.SessionTokenProvider
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothAudioQualityManager
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothPlaybackMonitor
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothStateManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground media playback service backed by Media3 (plan P1-3).
 *
 * Holds the app-wide [ExoPlayer] (built via [PlayerFactory]) and exposes it through a
 * [MediaSession]. The session token is published to [SessionTokenProvider] so the
 * [cn.com.dcsgo.mihx.player.PlaybackController] can attach a
 * [androidx.media3.session.MediaController].
 *
 * Notes:
 *  - `setSessionActivity` uses an *implicit* launcher `PendingIntent`: `:player` must not
 *    depend on `:app`, so referencing `MainActivity` directly would couple the modules.
 *  - `onTaskRemoved` / `onDestroy` read the last `mediaId` + `currentPosition` (P1-3); the
 *    `PlaybackStateRepository` persistence hook is wired in Phase 4.
 */
@AndroidEntryPoint
@UnstableApi
class AppMediaSessionService : MediaSessionService() {

    @Inject
    lateinit var playerFactory: PlayerFactory

    @Inject
    lateinit var sessionTokenProvider: SessionTokenProvider

    @Inject
    lateinit var bluetoothStateManager: BluetoothStateManager

    @Inject
    lateinit var bluetoothAudioQualityManager: BluetoothAudioQualityManager

    @Inject
    lateinit var playbackStateBuffer: PlaybackStateBuffer

    @Inject
    lateinit var playbackStateRepository: PlaybackStateRepository

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var bluetoothPlaybackMonitor: BluetoothPlaybackMonitor? = null

    override fun onCreate() {
        super.onCreate()
        // P3-1: provide Media3's default media notification. Media3 promotes this service to the
        // foreground and posts the notification automatically as soon as the playlist is non-empty
        // (i.e. on first play). Without it the service would never call startForeground(), which is
        // what triggered ForegroundServiceDidNotStartInTimeException when connected cold.
        setMediaNotificationProvider(DefaultMediaNotificationProvider(applicationContext))
        player = playerFactory.create(this)
        mediaSession = MediaSession.Builder(this, checkNotNull(player))
            .setSessionActivity(sessionActivityIntent())
            .build()
        sessionTokenProvider.publish(checkNotNull(mediaSession).token)
        // P3-4/5/6: observe Bluetooth state + auto-pause on audio-route loss + expose codec info.
        bluetoothStateManager.start()
        bluetoothAudioQualityManager.start()
        bluetoothPlaybackMonitor = BluetoothPlaybackMonitor(this, checkNotNull(player)).apply { start() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // P4-5: best-effort fallback save of the latest in-memory snapshot before the system
        // likely reclaims the process. Regular throttled saves cover the normal case.
        saveSnapshotFallback()
        player?.let { p ->
            val lastMediaId = p.currentMediaItem?.mediaId
            val lastPositionMs = p.currentPosition
            AppLogger.d(TAG, "onTaskRemoved lastMediaId=$lastMediaId posMs=$lastPositionMs")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        saveSnapshotFallback()
        bluetoothPlaybackMonitor?.stop()
        bluetoothPlaybackMonitor = null
        bluetoothStateManager.stop()
        bluetoothAudioQualityManager.stop()
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    private fun saveSnapshotFallback() {
        val snap = playbackStateBuffer.current.value ?: return
        saveScope.launch { runCatching { playbackStateRepository.saveSnapshot(snap) } }
    }

    private fun sessionActivityIntent(): PendingIntent {
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "AppMediaSessionService"
    }
}
