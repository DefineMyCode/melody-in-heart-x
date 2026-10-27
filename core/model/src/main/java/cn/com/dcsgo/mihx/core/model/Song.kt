package cn.com.dcsgo.mihx.core.model

import android.net.Uri
import androidx.compose.runtime.Stable

/**
 * 歌曲数据模型
 *
 * @property id            唯一标识符（自增，用于歌单关联和播放队列引用）
 * @property title         歌曲标题（取自元数据）
 * @property artist        艺术家名称
 * @property album         专辑名称（取自音频元数据，导入时记录）
 * @property sampleRate    采样率（Hz），用于区分同名歌曲的不同版本
 * @property uri           音频文件的 SAF URI（非本地导入的歌曲为 null）
 * @property albumArtUri   专辑封面 URI（缓存文件，null 表示无封面）
 * @property lrcUri        导入文件夹时匹配到的同目录 LRC 歌词 URI，null 表示未找到。
 * @property titleOverride 用户自定义的分组键覆盖值，null 时回退到 title。
 *                         用于"多版本管理"中将歌曲从某分组移出或关联到其他歌曲。
 */
@Stable
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val sampleRate: Int = 0,
    val uri: Uri? = null,
    val albumArtUri: Uri? = null,
    val lrcUri: Uri? = null,
    val titleOverride: String? = null,
    /** 专辑实体 ID（曲库持久化关联，未建立时为 null） */
    val albumId: Int? = null,
    /** 拆分后的歌手实体 ID 列表（曲库持久化关联） */
    val artistIds: List<Int> = emptyList(),
    /** 歌曲总时长（毫秒），导入时从元数据提取并持久化；未知时为 0 */
    val durationMs: Long = 0L,
    /** 文件指纹「size:mtime」，快速校验预筛用；null 表示尚未记录（视为未变化） */
    val fileFingerprint: String? = null,
) {
    /** 采样率格式化字符串，用于 UI 展示（如 "44kHz"） */
    val sampleRateDisplay: String
        get() = if (sampleRate > 0) "${sampleRate / 1000}kHz" else ""

    /**
     * 拆分后的歌手列表。
     *
     * 歌曲文件中的歌手属性可能包含多个歌手，通过 `/` 分隔（`/` 两边可能带空格）。
     * 例如 "A / B" → ["A", "B"]。无 `/` 时返回原始歌手。
     * 用于曲库歌手/专辑聚合与歌手详情关联，保证歌手为不可再拆的最小单位。
     *
     * 评审 2026-10-27 · M-13：原先是**每次访问都重新 split/map/filter** 的 getter，
     * 而它被用作排序比较器的键选择器（每次比较都调一次）、也被放进库级过滤循环里
     * ——1000 首库按歌手排序 ≈ 1 万次比较 × 约 4 次分配 ≈ 4 万个短命对象。
     *
     * 现在在**构造期算一次**并缓存。`private val` 不属于 data class 的
     * `equals`/`hashCode`/`copy` 契约，data class 语义完全不变。
     */
    val parsedArtists: List<String> = artist
        .split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(artist.trim()) }

    /**
     * 分组键：同名歌曲共享同一个 groupKey。
     * 优先使用用户设置的 titleOverride（允许手动调整归属分组），
     * 否则回退到原始标题。
     */
    val groupKey: String
        get() = titleOverride ?: title
}
