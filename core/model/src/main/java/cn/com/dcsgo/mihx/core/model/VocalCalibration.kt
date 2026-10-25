package cn.com.dcsgo.mihx.core.model

/**
 * 人声成分补偿 —— 把 valence 里「在测有没有人唱」的那部分剥掉。
 *
 * ## 为什么需要它
 *
 * 2026-10-25 全库 1095 首真值实测：valence 有一部分在测「有没有人唱」，
 * 而不是「什么情绪」。
 *
 * | 分组（按 vocalScore，阈值取 valence~vocalScore 的断点 0.010） | 首数 | valence 均值 |
 * |---|---|---|
 * | 人声组（vocalScore > 0.010） | 376 | **+0.120** |
 * | 器乐组（vocalScore ≤ 0.010） | 719 | **−0.041** |
 *
 * 组间差 −0.162，但**不能整份扣掉**：
 *
 * 1. 其中约一半是「人声多的歌也更响、更 energetic」带来的能量效应
 *    （arousal 现在是物理 RMS，是真实信号）。
 *    **在 arousal 分箱内匹配后**，残余差只有 **−0.091**。
 * 2. 独立模型对照（MusicNN + DEAM 头，64 首）：它自己的 arousal 匹配差是
 *    **+0.026（≈0）**，而现网同一切分下是 **−0.164**。
 *    —— 这层成分是现网独有的，不是「人声歌真的更正向」的音乐规律。
 *
 * 所以补偿量取 **arousal 匹配后的残余差 0.091**：
 * 只扣掉能量效应解释不了的那部分，保留「器乐曲确实更安静/更沉郁」的真实差异。
 *
 * ## 为什么是读时派生
 *
 * 补偿是**加在曲线上的常量平移**，不改变曲线本身 —— 库里存的仍是原始曲线，
 * 读的时候按 [EmotionModelSpec.VOCAL_COMP_SHIFT] 平移即可。
 *
 * 这样做的好处：
 *  - **零重扫**。改补偿量只要改规格清单里的常数，不用重跑模型；
 *  - 参数在 [EmotionModelSpec] 里（坐标系规格的单一真源），改它等于改坐标系，
 *    锚点表会跟着重标定（见 [EmotionGroup.ANCHORS_CALIBRATED_FOR]）。
 *
 * ## 边界
 *
 * - **vocalScore 为 null**（2026-10-25 之前分析的老行）→ 不补偿。
 *   这类行会被版本迁移补齐（见 data 层的 MIGRATION_14_15）。
 * - 补偿只动 valence，不动 arousal —— arousal 是物理响度，本来就没有"人声成分"。
 */
object VocalCalibration {

    /**
     * 该歌的 valence 补偿量（读时派生）。
     *
     * vocalScore 低于阈值 = 判定为器乐组 = 补偿；否则（有人声）不补偿。
     */
    fun shiftFor(vocalScore: Float?): Float =
        if (vocalScore != null && vocalScore <= EmotionModelSpec.VOCAL_COMP_THRESHOLD) {
            EmotionModelSpec.VOCAL_COMP_SHIFT
        } else {
            0f
        }

    /**
     * 补偿后的曲线：逐窗 valence 上移 [shiftFor] 的量，arousal 不动。
     *
     * 无需补偿时**原样返回**（同一实例），不产生额外分配。
     */
    fun adjustedCurve(
        curve: List<Pair<Float, Float>>,
        vocalScore: Float?,
    ): List<Pair<Float, Float>> {
        val shift = shiftFor(vocalScore)
        if (shift == 0f) return curve
        return curve.map { (v, a) -> (v + shift) to a }
    }
}
