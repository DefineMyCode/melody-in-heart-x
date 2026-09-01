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
    val format: String = "",
    val fileSize: String = "",
    val filePath: String = "",
    /** 整曲情绪分析结果(未分析时为 null) */
    val emotion: SongEmotion? = null,
)
