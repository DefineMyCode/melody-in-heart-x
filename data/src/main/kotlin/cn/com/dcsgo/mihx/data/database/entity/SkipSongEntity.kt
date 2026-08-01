package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Records how often a song was skipped (used by smart queue / "don't repeat" heuristics). */
@Entity(tableName = "skip_songs")
data class SkipSongEntity(
    @PrimaryKey val songId: Long,
    val skipCount: Int = 0,
)
