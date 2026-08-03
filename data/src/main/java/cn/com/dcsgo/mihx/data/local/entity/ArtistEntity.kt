package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 歌手实体（不可再拆分的最小单位）。
 *
 * 歌曲导入时若歌手属性包含 `/`，会按 `/` 拆分为多个原子歌手并各自建立一行。
 */
@Entity(
    tableName = "artists",
    indices = [Index(value = ["name"], unique = true)],
)
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val artistId: Int = 0,
    val name: String,
)
