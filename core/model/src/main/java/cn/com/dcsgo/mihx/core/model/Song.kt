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
 * @property titleOverride 手动分组覆盖值：存目标组的 groupKey（关联其他分组），
 *                         或 "标题#id"（移出分组）；null 时按 title + artist + album 自动分组。
 *                         用于"多版本管理"中将歌曲从某分组移出或关联到其他歌曲。
 */
@Stable
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val sampleRate: Int = 0,
    /** 原始位深（bit）；0 = 未知、有损格式或平台未提供 */
    val bitDepth: Int = 0,
    /** 比特率（kbps）；0 = 未知（主要给有损格式分级用） */
    val bitRateKbps: Int = 0,
    /** 声道数；0 = 未知 */
    val channelCount: Int = 0,
    /** 音频编码 MIME（如 audio/flac）；空 = 未知 */
    val audioCodec: String = "",
    /** 是否识别为空间音频/Atmos（仅 MIME 明确为 E-AC-3 JOC / AC-4 时置 true） */
    val spatialAudio: Boolean = false,
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

    /** 位深展示，如 "24bit"；未知时为空串 */
    val bitDepthDisplay: String
        get() = if (bitDepth > 0) "${bitDepth}bit" else ""

    /** 按文件实际规格计算的音质档位 */
    val qualityTier: AudioQualityTier
        get() = AudioQualityTier.classify(
            audioCodec = audioCodec,
            bitRateKbps = bitRateKbps,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
        )

    /** 档位文案；数据不足（未知）时为空串，UI 不展示 */
    val qualityLabel: String
        get() = qualityTier.label.takeIf { qualityTier != AudioQualityTier.UNKNOWN } ?: ""

    /** 声道展示，如 "6 声道"；未知时为空串 */
    val channelDisplay: String
        get() = if (channelCount > 0) "$channelCount 声道" else ""

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
     * 默认分组键：歌曲名、歌手、专辑**完全相同**才视为同一组。
     *
     * 用不可见分隔符拼接，避免三字段直接相连造成误判（如 "AB"+"C" 与 "A"+"BC"）。
     */
    val defaultGroupKey: String
        get() = "$title$GROUP_KEY_SEPARATOR$artist$GROUP_KEY_SEPARATOR$album"

    /**
     * 实际分组键。
     *
     * - 未手动调整：使用 [defaultGroupKey]（歌名 + 歌手 + 专辑全同）
     * - 手动关联：titleOverride 存目标组的 groupKey，直接沿用，从而并入目标组
     * - 手动移出：titleOverride 为 "标题#id"，与默认键不同，自成一类（组内仅 1 首）
     * - 旧版手动关联（titleOverride 只存纯标题）：键仍是旧值，但**不会**再与默认组
     *   （歌名 + 歌手 + 专辑）合并。这类老数据由 DB v19→v20 迁移一次性清空，升级后需重新关联。
     */
    val groupKey: String
        get() = titleOverride ?: defaultGroupKey

    /** 是否被手动移出分组（移出键形如 "标题#id"）。 */
    val isDetachedFromGroup: Boolean
        get() = titleOverride == "$title#$id"

    /** 手动关联目标的显示名；移出或未关联时回退为当前标题。 */
    val groupKeyLabel: String
        get() = titleOverride
            ?.takeUnless { isDetachedFromGroup }
            ?.substringBefore(GROUP_KEY_SEPARATOR)
            ?: title

    companion object {
        /** 分组键字段分隔符（U+0001，正常标题/歌手/专辑中不会出现）。 */
        const val GROUP_KEY_SEPARATOR = '\u0001'
    }
}
