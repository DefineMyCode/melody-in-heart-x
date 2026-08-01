package cn.com.dcsgo.mihx.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Foreground media playback service backed by Media3.
 *
 * This implementation is the Phase 0 seed of [docs/development-plan.md] P1-3: it satisfies
 * the `<service>` declaration in `app`'s manifest so the build's `MissingClass` lint gate is
 * green. The real playback kernel (PlayerFactory, PlaybackController, injected deps) lands in
 * Phase 1.
 *
 * Notes for Phase 1:
 *  - Add `@AndroidEntryPoint` and inject the `PlayerFactory` / `PlaybackController` once they
 *    exist. The player is built inline here only to keep Phase 0 dependency-light.
 *  - `setSessionActivity` uses an *implicit* launcher `PendingIntent` on purpose: `:player`
 *    must not depend on `:app`, so referencing `MainActivity` directly would couple the modules.
 *  - `onTaskRemoved` / `onDestroy` read the last `mediaId` + `currentPosition` (P1-3); the
 *    `PlaybackStateRepository` persistence hook is wired in Phase 4.
 */
@UnstableApi
class AppMediaSessionService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            // handleAudioBecomingNoisy = true: system pauses playback when audio output becomes noisy.
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .build()
        mediaSession = MediaSession.Builder(this, checkNotNull(player))
            .setSessionActivity(sessionActivityIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // P1-3: capture last played item + position. Phase 4 forwards this to PlaybackStateRepository.
        player?.let { p ->
            val lastMediaId = p.currentMediaItem?.mediaId
            val lastPositionMs = p.currentPosition
            // TODO(P4): PlaybackStateRepository.save(lastMediaId, lastPositionMs)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
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
}
