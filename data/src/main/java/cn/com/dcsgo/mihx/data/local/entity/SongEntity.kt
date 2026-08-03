package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    indices = [Index(value = ["uri"], unique = true)],
)
data class SongEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val artist: String,
    val sampleRate: Int,
    val uri: String?,
    val displayName: String?,
    val mimeType: String?,
    val lastModified: Long?,
    val size: Long?,
    val sourceTreeUri: String?,
    val albumArtCacheUri: String?,
    val lrcUri: String?,
    val importedAt: Long,
)
