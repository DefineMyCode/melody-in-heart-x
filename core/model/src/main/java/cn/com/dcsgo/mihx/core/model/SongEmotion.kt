package cn.com.dcsgo.mihx.core.model

/**
 * 歌曲情绪分析结果（域模型）.
 *
 * valence/arousal ∈ [-1,1]: 愉悦度 / 能量.
 * curve: 逐窗 (v,a) 序列，2.5s hop；渲染端做滑动平滑.
 * |valence| 或 |arousal| < 0.15 视为该区"未定"，UI 不硬判.
 */
data class SongEmotion(
    val songId: Int,
    val valence: Float,
    val arousal: Float,
    val curve: List<Pair<Float, Float>>,
    val peakSec: Float,
    val windowsAnalyzed: Int,
    val durationSec: Float,
    val modelVersion: String,
    val analyzedAt: Long,
    /** 整曲平均 YAMNet embedding (1024 维), 用户校准后 kNN 泛化用; 旧数据可为空 */
    val embedding: FloatArray? = null,
    /** 用户校准坐标 (多选词条换算); 非空时展示/推荐优先用它 */
    val userValence: Float? = null,
    val userArousal: Float? = null,
    /** 用户勾选的词条名 (EmotionCategory.label) */
    val userTags: List<String> = emptyList(),
) {
    /** 低置信: 均值落在未定带, UI 应显示"氛围:未定"而非具体象限 */
    val lowConfidence: Boolean
        get() = kotlin.math.abs(effectiveValence) < LOW_CONF ||
            kotlin.math.abs(effectiveArousal) < LOW_CONF

    /** 用户校准优先的有效坐标 */
    val effectiveValence: Float get() = userValence ?: valence
    val effectiveArousal: Float get() = userArousal ?: arousal

    /** 是否已由用户校准 */
    val userCorrected: Boolean get() = userValence != null && userArousal != null

    companion object {
        const val LOW_CONF = 0.15f
    }
}
