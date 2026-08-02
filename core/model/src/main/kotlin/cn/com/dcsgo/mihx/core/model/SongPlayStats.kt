package cn.com.dcsgo.mihx.core.model

/**
 * Aggregated playback statistics for one song (plan P5-C).
 *
 * [playCount] counts settled play sessions (a session with any real playback time), while
 * [totalPlayedMs] accumulates the wall-clock time the song was actually playing. [skipCount]
 * counts sessions the user actively skipped away from, and [shortPlayCount] the subset of those
 * that ended below the short-play threshold ("秒切").
 */
data class SongPlayStats(
    val songId: Long,
    val playCount: Long = 0L,
    val totalPlayedMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
    val skipCount: Int = 0,
    val shortPlayCount: Int = 0,
)
