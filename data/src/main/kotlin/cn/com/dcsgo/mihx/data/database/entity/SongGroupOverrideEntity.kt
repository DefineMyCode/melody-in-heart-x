package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User override for same-title multi-version grouping (plan §5). [groupKey] is the
 * [cn.com.dcsgo.mihx.core.model.Song.groupKey]; [preferredSongId] pins which version wins.
 */
@Entity(tableName = "song_group_overrides")
data class SongGroupOverrideEntity(
    @PrimaryKey val groupKey: String,
    val preferredSongId: Long,
    val updatedAt: Long = 0L,
)
