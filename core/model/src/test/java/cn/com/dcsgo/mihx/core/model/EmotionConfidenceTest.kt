package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 置信度（最高票占比）的契约（2026-10-25）。
 *
 * 信号 = 逐窗投票里最高票组的占比。这个量在 [EmotionGroup.categoriesFor]
 * 里本来就算出来了，但被 minRatio 过滤掉 —— 抽出来当置信度用，
 * 前提是**底座和展示层必须是同一份计算**，否则两边会分叉。
 */
class EmotionConfidenceTest {

    private val current = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
    private val legacy = EmotionGroup.anchorsFor(EmotionGroup.LEGACY_MODEL_LABEL)

    private fun emotion(
        curve: List<Pair<Float, Float>>,
        userTags: List<String> = emptyList(),
        modelVersion: String = EmotionGroup.CURRENT_MODEL_VERSION,
    ) = SongEmotion(
        songId = 1,
        valence = 0f,
        arousal = 0f,
        curve = curve,
        peakSec = 0f,
        windowsAnalyzed = curve.size,
        durationSec = 60f,
        modelVersion = modelVersion,
        analyzedAt = 0L,
        userTags = userTags,
    )

    @Test
    fun `voteRatios keeps groups the display layer would drop`() {
        // 9 窗 BRIGHT + 1 窗 EPIC -> EPIC 占 10%, 低于 15% 的展示门槛
        val curve = List(9) { current.getValue(EmotionGroup.BRIGHT) } +
            List(1) { current.getValue(EmotionGroup.EPIC) }
        val ratios = EmotionGroup.voteRatios(curve, current)
        assertEquals(2, ratios.size)
        assertEquals(0.9f, ratios[0].second, 1e-6f)
        assertEquals(0.1f, ratios[1].second, 1e-6f)
        // 展示层照样把它滤掉 —— 底座给全量，展示层自己筛
        assertEquals(1, EmotionGroup.categoriesFor(curve, current).size)
    }

    @Test
    fun `categoriesFor is the filtered view of voteRatios`() {
        val curve = List(6) { current.getValue(EmotionGroup.BRIGHT) } +
            List(3) { current.getValue(EmotionGroup.EPIC) } +
            List(1) { current.getValue(EmotionGroup.STILL) }
        val ratios = EmotionGroup.voteRatios(curve, current)
        val shown = EmotionGroup.categoriesFor(curve, current)
        assertEquals(ratios.take(shown.size), shown)
    }

    @Test
    fun `confidence is the top ratio`() {
        val curve = List(7) { current.getValue(EmotionGroup.BRIGHT) } +
            List(2) { current.getValue(EmotionGroup.EPIC) } +
            List(1) { current.getValue(EmotionGroup.STILL) }
        assertEquals(0.7f, EmotionGroup.confidenceOf(curve, current), 1e-6f)
    }

    @Test
    fun `confidence is zero when nothing votes`() {
        // 全是近零窗 -> 没有有效窗, 没有任何结论
        assertEquals(0f, EmotionGroup.confidenceOf(List(10) { 0.05f to 0.05f }, current), 1e-6f)
        assertEquals(0f, EmotionGroup.confidenceOf(emptyList(), current), 1e-6f)
    }

    @Test
    fun `spread votes lower confidence than concentrated ones`() {
        val concentrated = List(10) { current.getValue(EmotionGroup.BRIGHT) }
        val spread = listOf(
            current.getValue(EmotionGroup.BRIGHT),
            current.getValue(EmotionGroup.EPIC),
            current.getValue(EmotionGroup.STILL),
            current.getValue(EmotionGroup.CHILL),
            current.getValue(EmotionGroup.LOVE),
        )
        assertTrue(
            EmotionGroup.confidenceOf(concentrated, current) >
                EmotionGroup.confidenceOf(spread, current),
        )
    }

    @Test
    fun `confidence follows the anchor table of the curve own coordinate system`() {
        // 3 窗 legacy「放松」+ 7 窗 legacy「治愈」:
        //   按 legacy 锚点 -> 治愈 0.7 / 放松 0.3, top1 = 0.70
        //   按 current 锚点 -> 两组都落进「治愈」, top1 = 1.00
        // 曲线和锚点必须同坐标系, 否则重扫过渡期(old 曲线 + 新锚点)会算错.
        val curve = List(3) { legacy.getValue(EmotionGroup.CHILL) } +
            List(7) { legacy.getValue(EmotionGroup.LOVE) }
        assertEquals(0.7f, EmotionGroup.confidenceOf(curve, legacy), 1e-6f)
        assertEquals(1.0f, EmotionGroup.confidenceOf(curve, current), 1e-6f)
        // 读时派生必须自己选对坐标系
        assertEquals(
            0.7f,
            emotionConfidenceOf(emotion(curve, modelVersion = EmotionGroup.LEGACY_MODEL_LABEL))!!,
            1e-6f,
        )
        assertEquals(
            1.0f,
            emotionConfidenceOf(emotion(curve, modelVersion = EmotionGroup.CURRENT_MODEL_VERSION))!!,
            1e-6f,
        )
    }

    @Test
    fun `hint shows only when votes are spread`() {
        val spread = listOf(
            current.getValue(EmotionGroup.BRIGHT),
            current.getValue(EmotionGroup.EPIC),
            current.getValue(EmotionGroup.STILL),
            current.getValue(EmotionGroup.CHILL),
            current.getValue(EmotionGroup.LOVE),
        )
        assertTrue(emotionConfidenceOf(emotion(spread))!! < LOW_CONFIDENCE_RATIO)
        assertTrue(needsConfidenceHint(emotion(spread)))

        val concentrated = List(10) { current.getValue(EmotionGroup.BRIGHT) }
        assertFalse(needsConfidenceHint(emotion(concentrated)))
    }

    @Test
    fun `user tagged songs never show the hint`() {
        val spread = listOf(
            current.getValue(EmotionGroup.BRIGHT),
            current.getValue(EmotionGroup.EPIC),
            current.getValue(EmotionGroup.STILL),
            current.getValue(EmotionGroup.CHILL),
            current.getValue(EmotionGroup.LOVE),
        )
        // 展示的是用户自己选的词, 模型把握多大跟用户看到的词没关系
        assertFalse(needsConfidenceHint(emotion(spread, userTags = listOf("治愈"))))
    }

    @Test
    fun `songs without a curve never show the hint`() {
        assertNull(emotionConfidenceOf(emotion(emptyList())))
        assertFalse(needsConfidenceHint(emotion(emptyList())))
    }
}
