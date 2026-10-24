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
    /**
     * 用户是否**不认同**模型结论：true = 手动选的词条与模型自动词条不同（用户主动纠正）。
     *
     * null = 无模型输出可比（该歌未分析/分析失败，或历史数据在加此列之前），
     * false = 用户只是采纳了模型词条（点确认没改）。
     *
     * 存在的意义：校准弹窗预勾选模型词条，用户直接点「确认」即写回同一组词——
     * 这种"采纳"不是独立标注，混进训练集等于用模型输出训练模型。
     * 只有 true 的样本（外加未分析歌的纯人工标记）才是可作真值的独立纠正。
     */
    val userTagsEdited: Boolean? = null,
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
