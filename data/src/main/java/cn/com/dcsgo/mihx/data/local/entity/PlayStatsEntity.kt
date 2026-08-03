package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_stats")
data class PlayStatsEntity(
    @PrimaryKey val songId: Int,
    val playCount: Int,
    val rawPlayCount: Int,
    val totalDurationMs: Long,
    val lastPlayedAt: Long?,
)
