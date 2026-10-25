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
    /** 整曲平均 YAMNet embedding (1024 维), 供将来监督重训 VA 头按需取用; 旧数据可为空 */
    val embedding: FloatArray? = null,
    /**
     * 用户校准坐标 —— **已废弃, 不再作为真源**, 保留仅为兼容历史行。
     *
     * 坐标是**派生值**(词条 → 该坐标系下的锚点均值), 词条才是真值。
     * 存下来的坐标会过期: 曲线换坐标系(重扫/换模型)后, 存的坐标还停在旧坐标系
     * —— 实测重扫继承旧坐标后, 3 首用户校准的歌的坐标全错(见
     * docs/architecture/EMOTION_VA_UPGRADE.md §13)。
     * 改为读时按 [userTags] 现算, 见 [effectiveValence]。
     */
    val userValence: Float? = null,
    val userArousal: Float? = null,
    /** 用户勾选的词条名 (EmotionCategory.label) —— 用户校准的真值 */
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

    /**
     * 用户校准点的坐标 —— **读时派生**, 不读库里那两列。
     *
     * 坐标 = 用户勾选的词在**该行曲线自己的坐标系**下的锚点均值
     * (同坐标系由 [EmotionGroup.anchorsForManualPoint] 决定)。
     * 派生而非存储的理由:
     *  - 曲线换坐标系(重扫/换模型) → 词条不变, 坐标必须跟着换;
     *    存下来的坐标会过期(见 [userValence] 的说明)。
     *  - 用户改词 → 坐标自动跟着变, 不存在"两处不同步"。
     *
     * 无用户校准时返回 null, 回落模型均值。
     */
    private fun userPoint(): Pair<Float, Float>? =
        if (userTags.isEmpty()) {
            null
        } else {
            EmotionGroup.avgOfWords(userTags, EmotionGroup.anchorsForManualPoint(this))
        }

    /** 有效 valence: 用户校准优先(读时派生) */
    val effectiveValence: Float get() = userPoint()?.first ?: valence

    /** 有效 arousal: 用户校准优先(读时派生) */
    val effectiveArousal: Float get() = userPoint()?.second ?: arousal

    /** 是否已由用户校准(以**词条**为准, 坐标已不再落库) */
    val userCorrected: Boolean get() = userTags.isNotEmpty()

    companion object {
        const val LOW_CONF = 0.15f
    }
}
