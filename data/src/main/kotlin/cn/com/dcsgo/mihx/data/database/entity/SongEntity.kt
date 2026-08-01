package cn.com.dcsgo.mihx.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val albumArtUri: String? = null,
    val titleOverride: String? = null,
    val playable: Boolean = uri != null,
)
