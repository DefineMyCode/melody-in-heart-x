package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 人声成分补偿的契约（2026-10-25）。
 *
 * 补偿量来自全库 1095 首真值：valence 里有一部分在测「有没有人唱」。
 * 补偿是**读时派生**的 —— 库里存的曲线不动，读的时候才按 vocalScore 加偏移。
 * 所以这里锁三件事：
 *  1. 谁被补偿（vocalScore 低于阈值 = 器乐组）
 *  2. 补偿多少（常数，且只动 valence 不动 arousal）
 *  3. 没有 vocalScore 的老行不补偿（不能瞎补）
 */
class VocalCalibrationTest {

    private val threshold = EmotionModelSpec.VOCAL_COMP_THRESHOLD
    private val shift = EmotionModelSpec.VOCAL_COMP_SHIFT

    @Test
    fun `instrumental songs get compensated`() {
        assertEquals(shift, VocalCalibration.shiftFor(0f), 1e-6f)
        assertEquals(shift, VocalCalibration.shiftFor(threshold), 1e-6f)
        assertEquals(shift, VocalCalibration.shiftFor(threshold / 2f), 1e-6f)
    }

    @Test
    fun `songs with vocals are not compensated`() {
        assertEquals(0f, VocalCalibration.shiftFor(threshold * 2f), 1e-6f)
        assertEquals(0f, VocalCalibration.shiftFor(1f), 1e-6f)
    }

    @Test
    fun `songs without a vocal score are not compensated`() {
        // 老行(2026-10-25 之前分析的)没有这个值 —— 瞎补会把它们算错
        assertEquals(0f, VocalCalibration.shiftFor(null), 1e-6f)
    }

    @Test
    fun `adjusted curve shifts valence and leaves arousal untouched`() {
        val curve = listOf(0.1f to 0.4f, -0.3f to -0.2f)
        val adjusted = VocalCalibration.adjustedCurve(curve, 0f)
        assertEquals(curve.size, adjusted.size)
        assertEquals(0.1f + shift, adjusted[0].first, 1e-5f)
        assertEquals(0.4f, adjusted[0].second, 1e-6f)
        assertEquals(-0.3f + shift, adjusted[1].first, 1e-5f)
        assertEquals(-0.2f, adjusted[1].second, 1e-6f)
    }

    @Test
    fun `no compensation returns the very same instance`() {
        val curve = listOf(0.1f to 0.4f)
        assertSame(curve, VocalCalibration.adjustedCurve(curve, 1f))
        assertSame(curve, VocalCalibration.adjustedCurve(curve, null))
    }

    @Test
    fun `song emotion exposes the compensated curve and valence`() {
        val e = SongEmotion(
            songId = 1,
            valence = -0.05f,
            arousal = 0.2f,
            curve = listOf(0.1f to 0.4f, -0.2f to 0.0f),
            peakSec = 0f,
            windowsAnalyzed = 2,
            durationSec = 60f,
            modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
            analyzedAt = 0L,
            vocalScore = 0f,
        )
        assertEquals(-0.05f + shift, e.compensatedValence, 1e-5f)
        assertEquals(0.1f + shift, e.compensatedCurve[0].first, 1e-5f)
        // 用户校准优先于模型值 —— 校准过就不该被补偿值盖掉
        val user = e.copy(userTags = listOf("emo"))
        assertEquals(EmotionGroup.SAD.anchorV, user.effectiveValence, 1e-6f)
    }

    @Test
    fun `compensation pushes votes toward the positive side`() {
        // 「元气」(+0.590,+0.240) 与「治愈」(+0.390,+0.080) 间距 0.256 —— 全表最近的一对。
        // 取偏治愈一侧的加权点: 补偿(右移 0.091)之前归「治愈」, 之后跨过边界归「元气」。
        // 这就是补偿的核心作用: 把 valence 往更正面的方向推。
        val anchors = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
        val bright = anchors.getValue(EmotionGroup.BRIGHT)
        val love = anchors.getValue(EmotionGroup.LOVE)
        val point = 0.45f * bright.first + 0.55f * love.first to
            0.45f * bright.second + 0.55f * love.second
        val curve = listOf(point)

        val before = EmotionGroup.headlineTagsFor(curve, EmotionGroup.CURRENT_MODEL_VERSION)
        val after = EmotionGroup.headlineTagsFor(
            VocalCalibration.adjustedCurve(curve, 0f),
            EmotionGroup.CURRENT_MODEL_VERSION,
        )
        assertEquals(listOf("治愈"), before)
        assertEquals(listOf("元气"), after)
    }

    @Test
    fun `vocal songs keep their words`() {
        val anchors = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
        val curve = listOf(
            anchors.getValue(EmotionGroup.ABYSS),
            anchors.getValue(EmotionGroup.SAD),
            anchors.getValue(EmotionGroup.ANXIOUS),
        )
        val before = EmotionGroup.headlineTagsFor(curve, EmotionGroup.CURRENT_MODEL_VERSION)
        val after = EmotionGroup.headlineTagsFor(
            VocalCalibration.adjustedCurve(curve, 1f),
            EmotionGroup.CURRENT_MODEL_VERSION,
        )
        assertEquals(before, after)
    }

    @Test
    fun `null vocal score keeps legacy behaviour`() {
        val e = SongEmotion(
            songId = 1, valence = -0.05f, arousal = 0.2f,
            curve = listOf(0.1f to 0.4f), peakSec = 0f, windowsAnalyzed = 1,
            durationSec = 60f, modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
            analyzedAt = 0L, vocalScore = null,
        )
        assertNull(e.vocalScore)
        assertEquals(e.valence, e.compensatedValence, 1e-6f)
        assertFalse(e.compensatedCurve.any { it != e.curve[0] })
    }
}
