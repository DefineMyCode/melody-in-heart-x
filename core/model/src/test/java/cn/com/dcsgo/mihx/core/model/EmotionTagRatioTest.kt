package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 词条占比的契约（2026-10-25）。
 *
 * 占比**一直都在算** —— [EmotionGroup.voteRatios] / [EmotionGroup.categoriesFor]
 * 返回的就是 `Pair<组, 占比>`，只是 [EmotionGroup.headlineTagsFor] 一句
 * `.map { it.first.headline }` 把它扔了，API 和 UI 都只拿到词名。
 *
 * 「燃 35%、元气 22%」和「元气 55%、燃 20%」听感完全不同 ——
 * 光看词条名字分不出来，所以占比要露出来。
 */
class EmotionTagRatioTest {

    private val current = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)

    private fun emotion(
        curve: List<Pair<Float, Float>>,
        userTags: List<String> = emptyList(),
    ) = SongEmotion(
        songId = 1,
        valence = 0f,
        arousal = 0f,
        curve = curve,
        peakSec = 0f,
        windowsAnalyzed = curve.size,
        durationSec = 60f,
        modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
        analyzedAt = 0L,
        userTags = userTags,
    )

    @Test
    fun `each word carries its share of the whole song`() {
        // 6 窗元气 + 3 窗燃 + 3 窗静谧（共 12 窗，三组都过 15% 门槛）
        val curve = List(6) { current.getValue(EmotionGroup.BRIGHT) } +
            List(3) { current.getValue(EmotionGroup.RAGE) } +
            List(3) { current.getValue(EmotionGroup.STILL) }
        val ratios = emotionTagRatiosOf(emotion(curve))
        assertEquals(3, ratios.size)
        assertEquals("元气", ratios[0].tag)
        assertEquals(0.5f, ratios[0].ratio!!, 1e-6f)
        assertEquals("燃", ratios[1].tag)
        assertEquals(0.25f, ratios[1].ratio!!, 1e-6f)
        assertEquals("静谧", ratios[2].tag)
        assertEquals(0.25f, ratios[2].ratio!!, 1e-6f)
        // 占比之和为 1（所有组都过门槛时）
        val sum = ratios.fold(0f) { acc, r -> acc + r.ratio!! }
        assertEquals(1f, sum, 1e-6f)
    }

    @Test
    fun `display threshold drops the tiny ones`() {
        // 8 窗元气 + 1 窗燃 + 1 窗静谧 -> 燃 10% / 静谧 10% 低于 15% 门槛
        val curve = List(8) { current.getValue(EmotionGroup.BRIGHT) } +
            List(1) { current.getValue(EmotionGroup.RAGE) } +
            List(1) { current.getValue(EmotionGroup.STILL) }
        val shown = emotionTagRatiosOf(emotion(curve))
        assertEquals(1, shown.size)
        assertEquals("元气", shown[0].tag)
        assertEquals(0.8f, shown[0].ratio!!, 1e-6f)
        // 但完整分布拿得到
        val full = emotionTagRatiosOf(emotion(curve), minRatio = 0f)
        assertEquals(3, full.size)
        assertEquals(0.1f, full[1].ratio!!, 1e-6f)
    }

    @Test
    fun `user tagged words carry no ratio`() {
        // 用户选的是"我觉得这歌是它", 没有占比这回事 —— 不该编一个数出来
        val curve = List(10) { current.getValue(EmotionGroup.BRIGHT) }
        val ratios = emotionTagRatiosOf(emotion(curve, userTags = listOf("emo", "燃")))
        assertEquals(listOf("emo", "燃"), ratios.map { it.tag })
        assertTrue(ratios.all { it.ratio == null })
    }

    @Test
    fun `ratios come from the compensated curve`() {
        // 补偿会移动曲线, 占比必须跟着走 —— 否则展示的占比不是投票的占比
        val curve = List(6) { current.getValue(EmotionGroup.BRIGHT) } +
            List(3) { current.getValue(EmotionGroup.RAGE) } +
            List(1) { current.getValue(EmotionGroup.STILL) }
        val e = emotion(curve).copy(vocalScore = 0f)
        val ratios = emotionTagRatiosOf(e)
        // 补偿把整条曲线右移 -> 原本 3:1 的分配会变化, 占比必须反映补偿后的结果
        val expected = EmotionGroup.categoriesFor(
            curve = e.compensatedCurve,
            anchors = current,
        )
        assertEquals(expected.map { it.first.headline }, ratios.map { it.tag })
        assertEquals(expected.map { it.second }, ratios.map { it.ratio!! })
    }

    @Test
    fun `empty curve yields no ratios`() {
        val e = emotion(emptyList())
        assertTrue(emotionTagRatiosOf(e).isEmpty())
        assertNull(emotionTagRatiosOf(e).firstOrNull()?.ratio)
    }
}
