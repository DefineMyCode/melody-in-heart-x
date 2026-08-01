package cn.com.dcsgo.mihx.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.player.PlayerFactory
import cn.com.dcsgo.mihx.player.SessionTokenProvider
import dagger.hilt.android.AndroidEntryPoint
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

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = playerFactory.create(this)
        mediaSession = MediaSession.Builder(this, checkNotNull(player))
            .setSessionActivity(sessionActivityIntent())
            .build()
        sessionTokenProvider.publish(checkNotNull(mediaSession).token)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // P1-3: capture last played item + position. Phase 4 forwards this to PlaybackStateRepository.
        player?.let { p ->
            val lastMediaId = p.currentMediaItem?.mediaId
            val lastPositionMs = p.currentPosition
            AppLogger.d(TAG, "onTaskRemoved lastMediaId=$lastMediaId posMs=$lastPositionMs")
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
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
