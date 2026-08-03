package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "quick_skip_songs")
data class QuickSkipSongEntity(
    @PrimaryKey val songId: Int,
    val addedAt: Long,
)
