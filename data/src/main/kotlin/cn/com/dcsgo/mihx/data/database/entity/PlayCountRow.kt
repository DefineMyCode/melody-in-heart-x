package cn.com.dcsgo.mihx.data.database.entity

/**
 * Minimal projection of `play_stats` used as the uniform-random weight source (plan P5-C).
 *
 * Selecting only the two needed columns keeps the whole-library weight query cheap.
 */
data class PlayCountRow(
    val songId: Long,
    val playCount: Long,
)
