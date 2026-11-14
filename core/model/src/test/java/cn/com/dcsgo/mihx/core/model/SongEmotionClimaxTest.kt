package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播放页进度条高潮标记的域契约。
 *
 * [SongEmotion.climaxFraction] 只在"A 曲线峰值显著高于均值"时有值 —— else 会让
 * 全程高能的歌也在进度条上冒出一个点，失去"高潮"语义。阈值与歌曲信息弹窗
 * （`hasSignificantPeak(curve)`）共用 [SongEmotion.PEAK_MARGIN]。
 */
class SongEmotionClimaxTest {

    private fun emotion(
        curve: List<Pair<Float, Float>> = List(20) { 0.1f to 0.2f },
        peakSec: Float = 60f,
        windowsAnalyzed: Int = curve.size,
        durationSec: Float = 180f,
    ) = SongEmotion(
        songId = 1,
        valence = 0.1f,
        arousal = 0.2f,
        curve = curve,
        peakSec = peakSec,
        windowsAnalyzed = windowsAnalyzed,
        durationSec = durationSec,
        modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
        analyzedAt = 0L,
    )

    @Test
    fun `significant peak yields normalized position`() {
        val curve = List(20) { 0.0f to 0.0f }.toMutableList().also { it[4] = 0.0f to 0.9f }
        val e = emotion(curve = curve, peakSec = 10f, durationSec = 100f)

        assertTrue(e.hasSignificantPeak)
        assertEquals(0.1f, e.climaxFraction!!, 1e-6f)
    }

    @Test
    fun `flat loud song has no climax marker`() {
        val e = emotion(curve = List(20) { 0.0f to 0.8f })

        assertFalse(e.hasSignificantPeak)
        assertNull(e.climaxFraction)
    }

    @Test
    fun `user-only row has no climax marker`() {
        val e = emotion(windowsAnalyzed = 0)

        assertNull(e.climaxFraction)
    }

    @Test
    fun `zero duration has no climax marker`() {
        val curve = List(20) { 0.0f to 0.0f }.toMutableList().also { it[4] = 0.0f to 0.9f }
        val e = emotion(curve = curve, durationSec = 0f)

        assertNull(e.climaxFraction)
    }

    @Test
    fun `short curve is not significant`() {
        val e = emotion(curve = List(3) { 0.0f to 0.0f }, windowsAnalyzed = 3)

        assertFalse(e.hasSignificantPeak)
        assertNull(e.climaxFraction)
    }

    @Test
    fun `peak beyond duration is clamped`() {
        val curve = List(20) { 0.0f to 0.0f }.toMutableList().also { it[4] = 0.0f to 0.9f }
        val e = emotion(curve = curve, peakSec = 200f, durationSec = 180f)

        assertEquals(1f, e.climaxFraction!!, 1e-6f)
    }
}