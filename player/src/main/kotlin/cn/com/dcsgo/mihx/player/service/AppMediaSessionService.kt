package cn.com.dcsgo.mihx.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.player.PlaybackStateBuffer
import cn.com.dcsgo.mihx.player.PlayerFactory
import cn.com.dcsgo.mihx.player.SessionTokenProvider
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothAudioQualityManager
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothPlaybackMonitor
import cn.com.dcsgo.mihx.player.bluetooth.BluetoothStateManager
import cn.com.dcsgo.mihx.player.stats.PlayDurationTracker
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

    @Inject
    lateinit var playDurationTracker: PlayDurationTracker

    @Inject
    lateinit var settingsRepository: PlayerSettingsRepository

    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var bluetoothPlaybackMonitor: BluetoothPlaybackMonitor? = null

    override fun onCreate() {
        super.onCreate()
        AppLogger.d(TAG, "AppMediaSessionService onCreate starting...")
        // P3-1: 使用自定义 channel ID 彻底规避 Media3 默认 DEFAULT_CHANNEL_ID 的
        // IMPORTANCE_LOW 遗留渠道问题。一旦渠道创建，IMPORTANCE 不可通过代码修改——
        // 因此必须用全新的 ID 来确保 IMPORTANCE_DEFAULT + VISIBILITY_PUBLIC 生效。
        createMediaNotificationChannel()
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(MEDIA_CHANNEL_ID)
            .build()
        setMediaNotificationProvider(
            LockScreenAwareNotificationProvider(provider),
        )
        AppLogger.d(TAG, "Media notification provider set with channel=$MEDIA_CHANNEL_ID")
        player = playerFactory.create(this)
        mediaSession = MediaSession.Builder(this, checkNotNull(player))
            .setSessionActivity(sessionActivityIntent())
            .build()
        sessionTokenProvider.publish(checkNotNull(mediaSession).token)
        // 诊断：监听 player 状态变化——确认播放是否真正开始（startForeground 的前提）
        checkNotNull(player).addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) ||
                    events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) ||
                    events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)
                ) {
                    AppLogger.d(
                        TAG,
                        "Player state: state=${player.playbackState} isPlaying=${player.isPlaying} " +
                            "mediaItemCount=${player.mediaItemCount} currentMediaId=${player.currentMediaItem?.mediaId}",
                    )
                }
            }
        })
        // P3-4/5/6: observe Bluetooth state + auto-pause on audio-route loss + expose codec info.
        bluetoothStateManager.start()
        bluetoothAudioQualityManager.start()
        // P5-C4: the 蓝牙控制 toggle gates the auto-pause-on-route-loss monitor; when disabled the
        // monitor never registers, so a Bluetooth disconnect keeps playback running.
        val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        settingsScope.launch {
            if (settingsRepository.isBluetoothEnabled()) {
                bluetoothPlaybackMonitor = BluetoothPlaybackMonitor(this@AppMediaSessionService, checkNotNull(player))
                    .apply { start() }
            }
        }
        // P5-C: accrue listening time / skip signals on the service-side player so statistics keep
        // updating even when the UI process-side controller is gone.
        playDurationTracker.attach(checkNotNull(player))
        // 诊断：服务初始化完成后的状态快照
        logDiagnostics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val itemCount = player?.mediaItemCount ?: 0
        val isPlaying = player?.isPlaying ?: false
        AppLogger.d(
            TAG,
            "onStartCommand: flags=$flags startId=$startId mediaItemCount=$itemCount isPlaying=$isPlaying",
        )
        val result = super.onStartCommand(intent, flags, startId)
        AppLogger.d(TAG, "onStartCommand: super returned result=$result")
        return result
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        AppLogger.d(TAG, "onGetSession: client=${controllerInfo.packageName} sessionReady=${mediaSession != null}")
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // P4-5: best-effort fallback save of the latest in-memory snapshot before the system
        // likely reclaims the process. Regular throttled saves cover the normal case.
        saveSnapshotFallback()
        player?.let { p ->
            val lastMediaId = p.currentMediaItem?.mediaId
            val lastPositionMs = p.currentPosition
            AppLogger.d(TAG, "onTaskRemoved lastMediaId=$lastMediaId posMs=$lastPositionMs isPlaying=${p.isPlaying}")
        }
        // 播放中不从最近任务划掉而停播（媒体 App 标准行为）：Media3 默认 onTaskRemoved 会
        // stopSelf，导致后台音乐在用户划掉任务时中断。仅未播放时放行系统回收。
        if (player?.isPlaying != true) {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        saveSnapshotFallback()
        playDurationTracker.detach()
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
        val channel =
            NotificationChannel(
                MEDIA_CHANNEL_ID,
                "Melody 播放通知",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "本地音乐播放控制通知"
                setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                setBypassDnd(false)
            }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // delete() + create() 确保旧渠道（如存在）被替换，避免 IMPORTANCE 不可变的坑。
        // 仅在渠道 ID 升级时生效——新 ID 下 delete 是 no-op。
        notificationManager.deleteNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
        notificationManager.createNotificationChannel(channel)
        AppLogger.d(TAG, "Notification channel created: id=$MEDIA_CHANNEL_ID importance=DEFAULT lockscreenVisibility=PUBLIC")
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

    /**
     * 诊断：输出通知权限、渠道状态、服务前台状态等关键信息。
     * 在 logcat 过滤 "AppMediaSessionService" 即可看到全部诊断输出。
     */
    private fun logDiagnostics() {
        // 1. 通知权限（Android 13+）
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        AppLogger.d(TAG, "DIAG: notificationsEnabled=$notificationsEnabled")

        // 2. 渠道状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ourChannel = nm.getNotificationChannel(MEDIA_CHANNEL_ID)
            AppLogger.d(
                TAG,
                "DIAG: our channel=$MEDIA_CHANNEL_ID importance=${ourChannel?.importance} " +
                    "lockscreenVisibility=${ourChannel?.lockscreenVisibility} exists=${ourChannel != null}",
            )
            val defaultChannel = nm.getNotificationChannel(DefaultMediaNotificationProvider.DEFAULT_CHANNEL_ID)
            AppLogger.d(
                TAG,
                "DIAG: default channel exists=${defaultChannel != null} " +
                    "importance=${defaultChannel?.importance}",
            )
        }

        // 3. 列出所有通知渠道（帮助排查是否有渠道被用户禁用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notificationChannels.forEach { ch ->
                AppLogger.d(
                    TAG,
                    "DIAG: channel id=${ch.id} name=${ch.name} importance=${ch.importance} " +
                        "enabled=${ch.importance != NotificationManager.IMPORTANCE_NONE}",
                )
            }
        }
    }

    companion object {
        private const val TAG = "AppMediaSessionService"
        private const val MEDIA_CHANNEL_ID = "melody_media_playback_v1"
    }
}
