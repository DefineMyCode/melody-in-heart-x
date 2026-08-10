package cn.com.dcsgo.mihx.data.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.com.dcsgo.mihx.core.common.AppLogger
import cn.com.dcsgo.mihx.data.player.di.ApplicationScope
import cn.com.dcsgo.mihx.domain.repository.PlaylistResumeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Media3 playback service.
 *
 * Owns the ExoPlayer instance and exposes it through a Media3 MediaSession. The service relies on
 * MediaSessionService's default notification lifecycle and lets Media3 handle standard transport
 * commands against the playlist provided by the UI controller.
 */
@AndroidEntryPoint
class AppMediaSessionService : MediaSessionService() {

    companion object {
        private const val TAG = "AppMediaSessionService"
    }

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null

    @Inject
    lateinit var logger: AppLogger

    @Inject
    lateinit var playbackStateStore: PlaybackStateStore

    @Inject
    lateinit var playlistResumeRepository: PlaylistResumeRepository

    /** 进程级作用域:服务实例销毁后落盘协程仍需跑完,不能挂在服务自身的生命周期上。 */
    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        logger.info(TAG, "Service onCreate")
        createExoPlayer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        logger.info(TAG, "Service onDestroy")
        saveCurrentPlaybackSnapshot()
        playerListener?.let { exoPlayer?.removeListener(it) }
        playerListener = null
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        logger.info(TAG, "Service onTaskRemoved")
        saveCurrentPlaybackSnapshot()
        super.onTaskRemoved(rootIntent)
    }

    private fun createExoPlayer() {
        exoPlayer = PlayerFactory.create(this)

        createMediaSession()
        setupPlayerListener()

        logger.info(TAG, "ExoPlayer and Media3 MediaSession created")
    }

    private fun createMediaSession() {
        val player = exoPlayer ?: return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            launchIntent ?: Intent(Intent.ACTION_MAIN).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        logger.info(TAG, "Media3 MediaSession created")
    }

    private fun setupPlayerListener() {
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                logger.debug(TAG, "onIsPlayingChanged: $isPlaying")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                logger.debug(TAG, "onMediaItemTransition reason=$reason mediaId=${mediaItem?.mediaId}")
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                logger.debug(TAG, "onPlaybackStateChanged: $playbackState")
            }
        }
        exoPlayer?.addListener(playerListener!!)
    }

    /**
     * 记录退出时的播放快照。
     *
     * 快照(歌曲与进度)必须在调用线程同步读取——ExoPlayer 只能在其应用线程访问;而 DataStore 落盘一律
     * 交给进程级作用域异步执行,避免 onDestroy / onTaskRemoved 在主线程上等待磁盘 IO。
     */
    private fun saveCurrentPlaybackSnapshot() {
        val player = exoPlayer ?: return
        val songId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        val positionMs = player.currentPosition
        logger.debug(TAG, "saveCurrentPlaybackSnapshot: song=$songId position=${positionMs}ms")

        applicationScope.launch {
            playbackStateStore.persistCurrentPlaybackSnapshot(
                songId = songId,
                positionMs = positionMs,
            )
            // 歌单续播:若当前队列来自某个歌单,记录"实际在播"的歌曲并清除来源标记
            if (playlistResumeRepository.recordCurrentSource(songId)) {
                logger.debug(TAG, "recordPlaylistResume: song=$songId")
            }
        }
    }
}
