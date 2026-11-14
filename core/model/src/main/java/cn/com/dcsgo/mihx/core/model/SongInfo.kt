package cn.com.dcsgo.mihx.core.model

import androidx.compose.runtime.Stable

/**
 * 歌曲详细信息（用于歌曲信息 Dialog 展示）
 */
@Stable
data class SongInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: String = "",
    val bitRate: String = "",
    val sampleRate: String = "",
    /** 位深（如 "24bit"）；未知/有损格式时为空或 "未知" */
    val bitDepth: String = "",
    /** 音质档位（标准/较高/极高/无损/高解析）；数据不足时为空 */
    val quality: String = "",
    /** 音频编码 MIME，如 "audio/flac"；未知时为空 */
    val codec: String = "",
    /** 声道展示，如 "6 声道"；未知时为空 */
    val channelCount: String = "",
    /** 是否识别为空间音频/Atmos */
    val spatialAudio: Boolean = false,
    val format: String = "",
    val fileSize: String = "",
    val filePath: String = "",
    /** 整曲情绪分析结果(未分析时为 null) */
    val emotion: SongEmotion? = null,
)
