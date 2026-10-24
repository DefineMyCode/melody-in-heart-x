package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪词条语义的纯函数契约（2026-10-24）。
 *
 * 核心问题：校准弹窗**预勾选模型词条**，用户点「确认」即写回同一组词——
 * 那种「采纳」不是独立标注，混进训练集等于用模型输出训练模型。
 * [modelTagsOf] + [isUserCorrection] 负责把两者区分开。
 */
class EmotionTagsCorrectionTest {

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
        modelVersion = "test",
        analyzedAt = 0L,
        userTags = userTags,
    )

    /** 一条明确落在 BRIGHT 锚点附近的曲线 → 模型词条为「元气」 */
    private val brightCurve = List(20) { 0.6f to 0.4f }

    @Test
    fun `modelTagsOf returns the model conclusion ignoring user override`() {
        val e = emotion(brightCurve, userTags = listOf("emo"))

        // emotionTagsOf 在用户已校准时返回用户词；modelTagsOf 必须始终给模型结论
        assertEquals(listOf("emo"), emotionTagsOf(e))
        assertEquals(listOf("元气"), modelTagsOf(e))
    }

    @Test
    fun `modelTagsOf is empty when there is no curve`() {
        assertTrue(modelTagsOf(emotion(emptyList())).isEmpty())
    }

    @Test
    fun `isUserCorrection is null when there is no model output to compare`() {
        // 未分析/分析失败的歌：没有模型结论可说"改没改"
        assertNull(isUserCorrection(emptyList(), listOf("emo")))
    }

    @Test
    fun `isUserCorrection is false when user accepted the model tags`() {
        assertEquals(false, isUserCorrection(listOf("元气"), listOf("元气")))
    }

    @Test
    fun `isUserCorrection ignores tag order`() {
        assertEquals(false, isUserCorrection(listOf("元气", "治愈"), listOf("治愈", "元气")))
    }

    @Test
    fun `isUserCorrection is true when user changed the model tags`() {
        assertEquals(true, isUserCorrection(listOf("元气", "治愈"), listOf("emo")))
        // 少选一个也算改
        assertEquals(true, isUserCorrection(listOf("元气", "治愈"), listOf("元气")))
        // 多选一个也算改
        assertEquals(true, isUserCorrection(listOf("元气"), listOf("元气", "燃")))
    }
}
