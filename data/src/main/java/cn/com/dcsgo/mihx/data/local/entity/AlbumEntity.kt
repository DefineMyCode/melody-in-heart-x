package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 专辑实体。
 *
 * 一个专辑可关联多个歌手，通过 [SongArtistCrossRef] 与歌曲关联。
 */
@Entity(
    tableName = "albums",
    indices = [Index(value = ["name"], unique = true)],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val albumId: Int = 0,
    val name: String,
)
