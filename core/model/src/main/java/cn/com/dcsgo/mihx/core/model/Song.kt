package cn.com.dcsgo.mihx.core.model

import android.net.Uri

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
data class Song(
    val id: Int,
    val title: String,
    val artist: String,
    val album: String = "",
    val sampleRate: Int = 0,
    val uri: Uri? = null,
    val albumArtUri: Uri? = null,
    val lrcUri: Uri? = null,
    val titleOverride: String? = null
) {
    /** 采样率格式化字符串，用于 UI 展示（如 "44kHz"） */
    val sampleRateDisplay: String
        get() = if (sampleRate > 0) "${sampleRate / 1000}kHz" else ""

    /**
     * 分组键：同名歌曲共享同一个 groupKey。
     * 优先使用用户设置的 titleOverride（允许手动调整归属分组），
     * 否则回退到原始标题。
     */
    val groupKey: String
        get() = titleOverride ?: title
}
