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

/**
 * 情绪行的轻量投影: 与 [SongEmotionEntity] 同形, 但**不含 embeddingB64**.
 *
 * P0（2026-10-24）：embeddingB64 单行约 5.3KB base64（1024×float32）。
 * 全库 `SELECT *` 会把整库 embedding 物化成 String（1.6 万首 ≈ 90MB），
 * 再由仓储层解成 FloatArray（≈64MB）—— 而读路径（词条缓存/曲库情绪 Tab/时段统计）
 * 根本不碰 embedding，只用得到 V/A 曲线与用户校准三字段。
 * 改用本投影做查询，Room 直接跳过 embeddingB64 列。
 *
 * **表结构不变**（不是新实体），故不需要 Room 迁移；
 * embedding 仍随分析入库，作为未来监督重训 VA 头的原料，需要时走整行读（[SongEmotionEntity]）按需取。
 */
data class SongEmotionLite(
    val songId: Int,
    val valence: Float,
    val arousal: Float,
    val curveJson: String,
    val peakSec: Float,
    val windowsAnalyzed: Int,
    val durationSec: Float,
    val modelVersion: String,
    val analyzedAt: Long,
    val userValence: Float? = null,
    val userArousal: Float? = null,
    val userTags: String? = null,
)

/** 实体 → 轻量投影（丢弃 embeddingB64，不解码 base64） */
fun SongEmotionEntity.toLite(): SongEmotionLite = SongEmotionLite(
    songId = songId,
    valence = valence,
    arousal = arousal,
    curveJson = curveJson,
    peakSec = peakSec,
    windowsAnalyzed = windowsAnalyzed,
    durationSec = durationSec,
    modelVersion = modelVersion,
    analyzedAt = analyzedAt,
    userValence = userValence,
    userArousal = userArousal,
    userTags = userTags,
)
