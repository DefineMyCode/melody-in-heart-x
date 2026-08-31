package cn.com.dcsgo.mihx.ui.components

import cn.com.dcsgo.mihx.core.model.EmotionGroup
import cn.com.dcsgo.mihx.core.model.SongEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 情绪区纯函数: 平滑 / 高潮显著性 / 40 词表映射. */
class SongEmotionSectionTest {

    private fun emotion(
        v: Float = 0f,
        a: Float = 0f,
        curve: List<Pair<Float, Float>> = listOf(v to a),
        userTags: List<String> = emptyList(),
    ) = SongEmotion(
        songId = 1,
        valence = v,
        arousal = a,
        curve = curve,
        peakSec = 0f,
        windowsAnalyzed = curve.size,
        durationSec = 60f,
        modelVersion = "test",
        analyzedAt = 0L,
        userTags = userTags,
    )

    @Test
    fun `smoothCurve keeps length and shrinks noise`() {
        val noisy = List(11) { i -> (if (i % 2 == 0) 1f else -1f) to 0f }
        val smoothed = smoothCurve(noisy)
        assertEquals(noisy.size, smoothed.size)
        assertTrue(kotlin.math.abs(smoothed[5].first) < 0.5f)
    }

    @Test
    fun `smoothCurve passthrough short lists`() {
        assertEquals(2, smoothCurve(listOf(0.5f to 0f, -0.5f to 0f)).size)
        assertEquals(1, smoothCurve(listOf(0f to 0f)).size)
    }

    @Test
    fun `lowConfidence when either axis near zero`() {
        assertTrue(emotion(0.9f, 0.05f).lowConfidence)
        assertTrue(emotion(0.05f, 0.9f).lowConfidence)
        assertTrue(!emotion(0.9f, 0.9f).lowConfidence)
    }

    @Test
    fun `significant peak only when A spike stands out`() {
        val flat = List(20) { (it % 3) * 0.05f to ((it % 5) - 2) * 0.05f }
        assertTrue(!hasSignificantPeak(flat))
        val withSpike = List(20) { -0.2f to -0.3f } + listOf(0.5f to 0.6f) + List(20) { -0.2f to -0.3f }
        assertTrue(hasSignificantPeak(withSpike))
        assertTrue(!hasSignificantPeak(listOf(0f to 0.9f, 0f to 0.9f)))
    }

    @Test
    fun `word table covers 10 groups with unique index`() {
        val all = EmotionGroup.entries.flatMap { it.words }
        assertEquals(10, EmotionGroup.entries.size)
        assertEquals(39, all.size) // 9 组×4 + 窒息组×3
        assertEquals(all.size, all.toSet().size) // 无重复词
        EmotionGroup.entries.forEach { g ->
            assertEquals(g.headline, g.words.first())
            assertTrue(g.groupOfSelf() === g)
        }
    }

    private fun EmotionGroup.groupOfSelf() = EmotionGroup.groupOf(this.headline)

    @Test
    fun `categoriesFor picks nearest group by vote ratio`() {
        // 全程高能量+正 valence -> RAGE(0.3,0.85) 系
        val energetic = List(20) { 0.5f to 0.9f }
        val top = EmotionGroup.categoriesFor(energetic)
        assertTrue(top.isNotEmpty())
        assertEquals(EmotionGroup.RAGE, top.first().first)
        // 全零窗弃权 -> 空
        assertTrue(EmotionGroup.categoriesFor(List(10) { 0.05f to 0.05f }).isEmpty())
    }

    @Test
    fun `WITTY excluded from auto voting (center-vacuum fix)`() {
        // 鬼畜锚点(0.2,0.3)是 V-A 图中心, 中庸窗曾整片被吸成"鬼畜"
        val lukewarm = List(20) { 0.2f to 0.3f }
        val top = EmotionGroup.categoriesFor(lukewarm)
        assertTrue(top.none { it.first == EmotionGroup.WITTY })
        // 但手动标记链路(groupOf)不受限
        assertEquals(EmotionGroup.WITTY, EmotionGroup.groupOf("鬼畜"))
    }

    @Test
    fun `headlineTagsFor caps at MAX_TAGS`() {
        val mixed = List(5) { 0.6f to 0.4f } + List(5) { -0.5f to -0.4f } +
            List(5) { 0.3f to -0.55f } + List(5) { -0.4f to 0.6f } + List(5) { 0.4f to -0.3f }
        val tags = EmotionGroup.headlineTagsFor(mixed)
        assertTrue(tags.size <= EmotionGroup.MAX_TAGS)
    }

    @Test
    fun `emotionTagsOf user tags override model`() {
        val e = emotion(
            v = 0.8f, a = 0.8f,
            curve = List(20) { 0.8f to 0.8f },
            userTags = listOf("emo"),
        )
        assertEquals(listOf("emo"), emotionTagsOf(e))
    }

    @Test
    fun `emotionTagsOf empty when low confidence`() {
        assertTrue(emotionTagsOf(emotion(0.02f, 0.03f, List(10) { 0.02f to 0.03f })).isEmpty())
    }

    @Test
    fun `avgOfWords maps words to group anchors`() {
        // 同组词 -> 该组锚点
        val (v, a) = EmotionGroup.avgOfWords(listOf("伤感", "雨季"))!!
        assertEquals(EmotionGroup.SAD.anchorV, v, 1e-6f)
        assertEquals(EmotionGroup.SAD.anchorA, a, 1e-6f)
        // 跨组词 -> 锚点均值
        val (v2, a2) = EmotionGroup.avgOfWords(listOf("燃", "静谧"))!!
        assertEquals(
            (EmotionGroup.RAGE.anchorV + EmotionGroup.STILL.anchorV) / 2, v2, 1e-6f
        )
        assertTrue(a2 > 0f) // RAGE 拉高
        // 非法词 -> null
        assertEquals(null, EmotionGroup.avgOfWords(listOf("不存在的词")))
        assertEquals(null, EmotionGroup.avgOfWords(emptyList()))
    }
}
