package cn.com.dcsgo.mihx.core.model

import android.net.Uri

/**
 * 单个专辑聚合数据（专辑可与多个歌手关联）。
 *
 * 由持久化的 `albums` 表聚合而来；在无 Room 的旧版路径下也可由歌曲列表派生。
 */
data class AlbumEntry(
    val name: String,
    /** 该专辑关联的歌手（拆分后的最小单位） */
    val artistNames: List<String>,
    val songCount: Int,
    val coverUri: Uri?,
)
