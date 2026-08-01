package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Counts plays that ended very early (e.g. <30s) — a signal of an unwanted track. */
@Entity(tableName = "short_play_counts")
data class ShortPlayCountEntity(
    @PrimaryKey val songId: Long,
    val shortPlayCount: Int = 0,
)
