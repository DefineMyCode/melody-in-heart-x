package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-song playback statistics (plan P5-C).
 *
 * [playCount] counts settled sessions; [totalPlayedMs] accumulates the actual playing time so the
 * stats screen can show a real listening total rather than `count * duration`.
 */
@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val songId: Long,
    val playCount: Long = 0L,
    val totalPlayedMs: Long = 0L,
    val lastPlayedAt: Long = 0L,
)
