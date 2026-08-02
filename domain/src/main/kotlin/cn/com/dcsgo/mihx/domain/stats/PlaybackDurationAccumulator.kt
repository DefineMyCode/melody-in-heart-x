package cn.com.dcsgo.mihx.domain.stats

/**
 * Outcome of one settled play session (plan P5-C).
 *
 * [playedMs] is the accumulated wall-clock time the song was actually playing (paused stretches
 * excluded). [completed] is true when playback reached the end of the item naturally; [shortPlay]
 * marks a "秒切" — the user skipped away before the short-play threshold elapsed.
 */
data class PlaySessionResult(
    val songId: Long,
    val playedMs: Long,
    val completed: Boolean,
    val shortPlay: Boolean,
)

/**
 * Pure state machine that turns transport events into per-song play durations (plan P5-C).
 *
 * Deliberately Android-free so it can be unit tested on the JVM: callers feed wall-clock
 * timestamps, the accumulator only adds up the stretches during which playback was running.
 *
 * Usage: call [onPlayingChanged] whenever `isPlaying` flips, [onSongChanged] on every media item
 * transition (which settles and returns the *previous* song's session), and [finish] when playback
 * ends or the player is released.
 */
class PlaybackDurationAccumulator(
    private val shortPlayThresholdMs: Long = DEFAULT_SHORT_PLAY_THRESHOLD_MS,
) {

    private var currentSongId: Long? = null
    private var accumulatedMs = 0L
    private var resumedAtMs: Long? = null
    private var playing = false

    /** Registers a play/pause flip. Repeated identical states are ignored. */
    fun onPlayingChanged(isPlaying: Boolean, nowMs: Long) {
        if (isPlaying == playing) return
        playing = isPlaying
        if (isPlaying) {
            resumedAtMs = nowMs
        } else {
            flushElapsed(nowMs)
        }
    }

    /**
     * Switches to [songId], settling the previous song. [previousCompleted] must be true only when
     * the transition happened because the previous item played to its end.
     */
    fun onSongChanged(songId: Long?, nowMs: Long, previousCompleted: Boolean): PlaySessionResult? {
        val result = settle(nowMs, previousCompleted)
        currentSongId = songId
        accumulatedMs = 0L
        resumedAtMs = if (playing) nowMs else null
        return result
    }

    /** Settles the current song (end of playback / player release) and clears the state. */
    fun finish(nowMs: Long, completed: Boolean): PlaySessionResult? {
        val result = settle(nowMs, completed)
        currentSongId = null
        accumulatedMs = 0L
        resumedAtMs = null
        return result
    }

    private fun settle(nowMs: Long, completed: Boolean): PlaySessionResult? {
        flushElapsed(nowMs)
        val songId = currentSongId ?: return null
        val playedMs = accumulatedMs
        accumulatedMs = 0L
        // Nothing was actually played and the item did not finish — not a session worth recording.
        if (playedMs <= 0L && !completed) return null
        return PlaySessionResult(
            songId = songId,
            playedMs = playedMs,
            completed = completed,
            shortPlay = !completed && playedMs < shortPlayThresholdMs,
        )
    }

    private fun flushElapsed(nowMs: Long) {
        val startedAt = resumedAtMs ?: return
        accumulatedMs += (nowMs - startedAt).coerceAtLeast(0L)
        resumedAtMs = null
    }

    companion object {
        /** Plays shorter than this that the user skipped away from count as "秒切". */
        const val DEFAULT_SHORT_PLAY_THRESHOLD_MS = 30_000L
    }
}
