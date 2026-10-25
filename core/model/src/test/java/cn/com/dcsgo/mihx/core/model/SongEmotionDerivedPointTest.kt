package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 用户校准坐标**读时派生**的契约（2026-10-25）。
 *
 * 词条是真值，坐标是派生值：坐标 = 用户勾选的词在**该行曲线自己的坐标系**
 * 下的锚点均值。
 *
 * 原来是"存坐标"：标记时按当时的锚点算好写进 userValence/userArousal，重扫时
 * 原样继承 —— 曲线换了坐标系，坐标还停在旧的（实测真机 3 首用户校准的歌
 * 坐标全错：存的 v=+0.250 a=+0.675，当前坐标系应是 v=+0.119 a=+0.442）。
 */
class SongEmotionDerivedPointTest {

    private fun emotion(
        modelVersion: String = EmotionGroup.CURRENT_MODEL_VERSION,
        userTags: List<String> = emptyList(),
        windowsAnalyzed: Int = 20,
        valence: Float = 0.2f,
        arousal: Float = 0.8f,
    ) = SongEmotion(
        songId = 1,
        valence = valence,
        arousal = arousal,
        curve = List(20) { 0.1f to 0.2f },
        peakSec = 2.5f,
        windowsAnalyzed = windowsAnalyzed,
        durationSec = 10f,
        modelVersion = modelVersion,
        analyzedAt = 0L,
        userTags = userTags,
    )

    @Test
    fun `no user tags falls back to model coordinates`() {
        val e = emotion()
        assertEquals(0.2f, e.effectiveValence, 1e-6f)
        assertEquals(0.8f, e.effectiveArousal, 1e-6f)
        assertEquals(false, e.userCorrected)
    }

    @Test
    fun `user tags derive the point in the row's own coordinate system`() {
        // 当前坐标系: 燃 = RAGE(+0.009, +0.607)
        val current = emotion(userTags = listOf("燃"))
        assertEquals(EmotionGroup.RAGE.anchorV, current.effectiveValence, 1e-6f)
        assertEquals(EmotionGroup.RAGE.anchorA, current.effectiveArousal, 1e-6f)
        assertEquals(true, current.userCorrected)

        // 旧坐标系: 燃 = RAGE(+0.30, +0.85) —— 同一组词, 坐标随坐标系变
        val legacy = emotion(modelVersion = "yamnet-va-v3", userTags = listOf("燃"))
        assertEquals(0.3f, legacy.effectiveValence, 1e-6f)
        assertEquals(0.85f, legacy.effectiveArousal, 1e-6f)
    }

    @Test
    fun `stored coordinates are ignored so they cannot go stale`() {
        // 库里那两列即使有值(历史数据), 也不参与计算
        val e = SongEmotion(
            songId = 1,
            valence = 0.2f,
            arousal = 0.8f,
            curve = List(20) { 0.1f to 0.2f },
            peakSec = 2.5f,
            windowsAnalyzed = 20,
            durationSec = 10f,
            modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
            analyzedAt = 0L,
            userValence = 0.25f,
            userArousal = 0.675f,
            userTags = listOf("燃"),
        )
        assertEquals(EmotionGroup.RAGE.anchorV, e.effectiveValence, 1e-6f)
        assertEquals(EmotionGroup.RAGE.anchorA, e.effectiveArousal, 1e-6f)
    }

    @Test
    fun `user-only row derives the point with the current anchor table`() {
        // 无曲线 -> 未来的曲线必是当前坐标系 -> 用当前锚点
        val e = emotion(userTags = listOf("治愈"), windowsAnalyzed = 0)
        assertEquals(EmotionGroup.LOVE.anchorV, e.effectiveValence, 1e-6f)
        assertEquals(EmotionGroup.LOVE.anchorA, e.effectiveArousal, 1e-6f)
    }

    @Test
    fun `multiple tags average within the row's coordinate system`() {
        // 燃 + 神圣 在当前坐标系: RAGE(+0.009,+0.607) 与 EPIC(+0.228,+0.277) 的均值
        val e = emotion(userTags = listOf("燃", "神圣"))
        assertEquals(
            (EmotionGroup.RAGE.anchorV + EmotionGroup.EPIC.anchorV) / 2f,
            e.effectiveValence,
            1e-6f,
        )
        assertEquals(
            (EmotionGroup.RAGE.anchorA + EmotionGroup.EPIC.anchorA) / 2f,
            e.effectiveArousal,
            1e-6f,
        )
    }
}
