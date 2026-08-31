package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 歌曲情绪分析结果（整曲逐窗 V/A 曲线 + 全曲均值）.
 *
 * curveJson: 逐窗 [v,a,v,a,...] float 数组 JSON（原始未平滑，渲染端平滑）.
 * modelVersion: 分析时的模型标识，换模型后可据此重扫.
 */
@Entity(tableName = "song_emotions")
data class SongEmotionEntity(
    @PrimaryKey val songId: Int,
    val valence: Float,
    val arousal: Float,
    val curveJson: String,
    /** A 轴（能量）正向峰值所在秒数 */
    val peakSec: Float,
    val windowsAnalyzed: Int,
    val durationSec: Float,
    val modelVersion: String,
    val analyzedAt: Long,
    /** 整曲平均 embedding, base64(float32[1024]) */
    val embeddingB64: String? = null,
    val userValence: Float? = null,
    val userArousal: Float? = null,
    /** 用户勾选词条, 逗号分隔的 label */
    val userTags: String? = null,
)
