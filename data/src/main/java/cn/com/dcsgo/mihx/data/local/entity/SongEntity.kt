package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "songs",
    // uri 唯一索引：同一文件不重复入库。
    // albumId 索引（评审 2026-10-27 · M-5）：专辑目录查询与「孤儿专辑清理」都按
    // albumId 关联/扫描 songs 表，没有索引就是整表扫描；而孤儿清理在每次持久化
    // 以及每次删歌/去重/文件校验时都会跑。
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["albumId"]),
    ],
)
data class SongEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: Int? = null,
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
    /** 歌曲总时长（毫秒），导入时从元数据提取；未知时为 0 */
    val durationMs: Long = 0L,
)
