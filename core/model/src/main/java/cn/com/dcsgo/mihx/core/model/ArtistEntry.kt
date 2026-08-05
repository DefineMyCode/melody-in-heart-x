package cn.com.dcsgo.mihx.core.model

import android.net.Uri
import androidx.compose.runtime.Stable

/**
 * 单个歌手聚合数据（歌手为不可再拆分的最小单位）。
 *
 * 由持久化的 `artists` 表聚合而来；在无 Room 的旧版路径下也可由歌曲内存列表派生。
 */
@Stable
data class ArtistEntry(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val coverUri: Uri?,
)
