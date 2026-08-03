package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_group_overrides")
data class SongGroupOverrideEntity(
    @PrimaryKey val songId: Int,
    val titleOverride: String?,
    val updatedAt: Long,
)
