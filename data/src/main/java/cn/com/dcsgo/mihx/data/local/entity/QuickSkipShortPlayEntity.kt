package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_skip_short_play_counts")
data class QuickSkipShortPlayEntity(
    @PrimaryKey val songId: Int,
    val count: Int,
    val updatedAt: Long,
)
