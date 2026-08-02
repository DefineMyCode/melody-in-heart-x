package cn.com.dcsgo.mihx.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        // P3-1: create the media notification channel ourselves BEFORE Media3 does. Media3's
        // DefaultMediaNotificationProvider builds its channel with IMPORTANCE_LOW and does not set
        // lockscreenVisibility explicitly; several OEM skins (MIUI/HyperOS, ColorOS, One UI, EMUI)
        // suppress the lock-screen media card for low-importance / non-explicitly-public channels
        // even though the notification itself is VISIBILITY_PUBLIC. We pre-create the channel with
        // DEFAULT importance + explicit VISIBILITY_PUBLIC (still silent) so the card renders on the
        // lock screen. DefaultMediaNotificationProvider reuses this channel via its DEFAULT_CHANNEL_ID
        // because ensureNotificationChannel() skips creation when the channel already exists.
        createMediaNotificationChannel()
        // P3-1: provide a lock-screen-aware media notification provider.
        // DefaultMediaNotificationProvider builds the notification but does not guarantee
        // VISIBILITY_PUBLIC, so some devices suppress the media card on the lock screen
        // while still showing it in the notification shade / quick-settings panel.
        setMediaNotificationProvider(LockScreenAwareNotificationProvider(DefaultMediaNotificationProvider(this)))
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

    /**
     * Creates (or updates) the media notification channel that [DefaultMediaNotificationProvider]
     * posts to. Called before [setMediaNotificationProvider] so our settings win: Media3 only
     * creates the channel when `getNotificationChannel(id) == null`, so the pre-created channel with
     * DEFAULT importance + explicit VISIBILITY_PUBLIC is reused. Re-calling createNotificationChannel
     * on an existing id also updates importance / lockscreenVisibility in place, so this also
     * migrates channels left behind by older builds (which used IMPORTANCE_LOW).
     *
     * Uses the framework NotificationManager/NotificationChannel only — never NotificationCompat —
     * to stay within architecture gate A7 for the :player module.
     */
    private fun createMediaNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channelId = DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID
        val channel =
            NotificationChannel(
                channelId,
                "Melody 播放通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "本地音乐播放控制通知"
                setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                // Keep it silent: DEFAULT importance (not LOW) makes OEM lock-screen media cards
                // treat it as a real media notification, but we don't want audible alerts.
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                setBypassDnd(false)
            }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
