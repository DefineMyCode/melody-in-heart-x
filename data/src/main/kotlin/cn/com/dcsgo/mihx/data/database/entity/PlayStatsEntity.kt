package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-song playback statistics (play count + last played timestamp). */
@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val songId: Long,
    val playCount: Long = 0L,
    val lastPlayedAt: Long = 0L,
)
