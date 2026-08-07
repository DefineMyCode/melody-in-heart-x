package cn.com.dcsgo.mihx.data.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.com.dcsgo.mihx.core.common.AppLogger
import cn.com.dcsgo.mihx.domain.repository.PlaylistResumeRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

    private fun saveCurrentPlaybackSnapshot() {
        val player = exoPlayer ?: return
        val songId = player.currentMediaItem?.mediaId?.toIntOrNull() ?: return
        playbackStateStore.saveCurrentPlaybackSnapshot(
            songId = songId,
            positionMs = player.currentPosition,
        )
        // 歌单续播:若当前队列来自某个歌单,记录"实际在播"的歌曲并清除来源标记
        if (playlistResumeRepository.recordCurrentSourceBlocking(songId)) {
            logger.debug(TAG, "recordPlaylistResume: song=$songId")
        }
        logger.debug(TAG, "saveCurrentPlaybackSnapshot: song=$songId position=${player.currentPosition}ms")
    }
}
