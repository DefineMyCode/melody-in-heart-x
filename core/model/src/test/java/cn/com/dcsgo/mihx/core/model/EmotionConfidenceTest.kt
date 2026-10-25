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
        vocalScore: Float? = null,
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
        vocalScore = vocalScore,
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
        // 契约: 置信度必须用**曲线自己坐标系**的锚点表算。
        // 锁"契约"而不是锁"数值" —— 锚点表会随坐标系重标定而变(§16),
        // 把具体数值写进断言会让每次重标定都要改测试, 反而容易漏改。
        val curve = List(3) { legacy.getValue(EmotionGroup.CHILL) } +
            List(7) { legacy.getValue(EmotionGroup.LOVE) }
        for (ver in listOf(
            EmotionGroup.LEGACY_MODEL_LABEL,
            EmotionGroup.V4_MODEL_LABEL,
            EmotionGroup.CURRENT_MODEL_VERSION,
        )) {
            val e = emotion(curve, modelVersion = ver)
            assertEquals(
                EmotionGroup.confidenceOf(curve, EmotionGroup.anchorsFor(ver)),
                emotionConfidenceOf(e)!!,
                1e-6f,
            )
        }
        // 读时派生与"自己选表"必须给出同一个答案(上面那条对所有版本成立,
        // 正是"没选错表"的证据): 若实现偷懒总用当前表, legacy 那条会红。
    }

    @Test
    fun `different coordinate systems give different tags for the same curve`() {
        // 同一段曲线, 换坐标系 -> 词的归属必须跟着换(否则说明选表逻辑没生效)
        val curve = listOf(
            legacy.getValue(EmotionGroup.EPIC),
            legacy.getValue(EmotionGroup.BRIGHT),
            legacy.getValue(EmotionGroup.STILL),
        )
        val asLegacy = EmotionGroup.headlineTagsFor(curve, EmotionGroup.LEGACY_MODEL_LABEL)
        val asCurrent = EmotionGroup.headlineTagsFor(curve, EmotionGroup.CURRENT_MODEL_VERSION)
        assertTrue(asLegacy.isNotEmpty() && asCurrent.isNotEmpty())
        assertTrue(
            "两套坐标系应给出不同词条: legacy=$asLegacy current=$asCurrent",
            asLegacy != asCurrent,
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

    @Test
    fun `confidence is computed on the compensated curve`() {
        // 词条是按**补偿后**的曲线投票的, 置信度必须描述**同一次投票**。
        // 用补偿前的曲线算 = 在描述一个已经不存在的投票过程。
        val anchors = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
        val bright = anchors.getValue(EmotionGroup.BRIGHT)
        val love = anchors.getValue(EmotionGroup.LOVE)
        // 偏治愈一侧的加权点: 补偿(右移 0.091)之前归「治愈」, 之后跨过边界归「元气」
        val point = 0.45f * bright.first + 0.55f * love.first to
            0.45f * bright.second + 0.55f * love.second
        val curve = List(3) { point } + listOf(bright)

        // 补偿前: 治愈 3 票 / 元气 1 票 -> 0.75
        assertEquals(0.75f, EmotionGroup.confidenceOf(curve, anchors), 1e-6f)

        // 补偿后: 全部归元气 -> 1.00（器乐组 vocalScore 低 -> 会补偿）
        val e = emotion(curve, vocalScore = 0f)
        assertEquals(1.0f, emotionConfidenceOf(e)!!, 1e-6f)

        // 有人声的歌不补偿 -> 置信度不变
        val voiced = emotion(curve, vocalScore = 1f)
        assertEquals(0.75f, emotionConfidenceOf(voiced)!!, 1e-6f)
    }
}
