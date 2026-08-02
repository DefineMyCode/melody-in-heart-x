package cn.com.dcsgo.mihx.player.stats

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.stats.PlaySessionResult
import cn.com.dcsgo.mihx.domain.stats.PlaybackDurationAccumulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records per-song listening time and skip signals (plan P5-C).
 *
 * Attached to the service-side [Player] (not the UI [androidx.media3.session.MediaController]) so
 * statistics keep accruing while the app UI is gone. All duration arithmetic lives in the
 * Android-free [PlaybackDurationAccumulator]; this class only translates player callbacks into
 * accumulator events and persists the settled sessions.
 *
 * Skip semantics: a transition whose reason is not `AUTO`/`REPEAT` means the user moved away
 * deliberately, which counts as a skip; below the short-play threshold it additionally counts as
 * a "秒切" (short play).
 */
@Singleton
class PlayDurationTracker @Inject constructor(
    private val playStatsRepository: PlayStatsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accumulator = PlaybackDurationAccumulator()
    private var attachedPlayer: Player? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            accumulator.onPlayingChanged(isPlaying, System.currentTimeMillis())
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val completed = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            persist(
                accumulator.onSongChanged(
                    songId = mediaItem?.mediaId?.toLongOrNull(),
                    nowMs = System.currentTimeMillis(),
                    previousCompleted = completed,
                ),
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                persist(accumulator.finish(System.currentTimeMillis(), completed = true))
            }
        }
    }

    /** Starts tracking [player]. Idempotent: re-attaching the same player is a no-op. */
    fun attach(player: Player) {
        if (attachedPlayer === player) return
        detach()
        attachedPlayer = player
        player.addListener(listener)
        // The player may already be playing an item when we attach (service restart).
        player.currentMediaItem?.mediaId?.toLongOrNull()?.let { songId ->
            persist(
                accumulator.onSongChanged(songId, System.currentTimeMillis(), previousCompleted = false),
            )
        }
        accumulator.onPlayingChanged(player.isPlaying, System.currentTimeMillis())
    }

    /** Settles the in-flight session and stops tracking. */
    fun detach() {
        val player = attachedPlayer ?: return
        player.removeListener(listener)
        attachedPlayer = null
        persist(accumulator.finish(System.currentTimeMillis(), completed = false))
    }

    private fun persist(result: PlaySessionResult?) {
        val session = result ?: return
        scope.launch {
            runCatching {
                playStatsRepository.recordPlay(session.songId, session.playedMs)
                if (!session.completed) {
                    playStatsRepository.recordSkip(session.songId)
                    if (session.shortPlay) {
                        playStatsRepository.recordShortPlay(session.songId)
                    }
                }
            }.onFailure { AppLogger.w(TAG, "Failed to persist play stats.") }
        }
    }

    companion object {
        private const val TAG = "PlayDurationTracker"
    }
}
