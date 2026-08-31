package cn.com.dcsgo.mihx.domain.emotion

import cn.com.dcsgo.mihx.core.model.SongEmotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** kNN 个性化纯函数测试. */
class EmotionPersonalizerTest {

    private fun emo(
        id: Int,
        emb: FloatArray?,
        uv: Float? = null,
        ua: Float? = null,
    ) = SongEmotion(
        songId = id,
        valence = 0f,
        arousal = 0f,
        curve = listOf(0f to 0f),
        peakSec = 0f,
        windowsAnalyzed = 1,
        durationSec = 60f,
        modelVersion = "t",
        analyzedAt = 0L,
        embedding = emb,
        userValence = uv,
        userArousal = ua,
    )

    private fun emb(vararg v: Float) = FloatArray(v.size) { v[it] }

    @Test
    fun `predicts from nearest anchors`() {
        val a1 = emo(1, emb(1f, 0f), uv = -0.8f, ua = -0.4f) // "冷"锚点
        val a2 = emo(2, emb(0f, 1f), uv = 0.8f, ua = 0.8f) // "热"锚点
        val a3 = emo(3, emb(0.9f, 0.1f), uv = -0.6f, ua = -0.2f) // 接近 a1
        val target = emo(9, emb(0.95f, 0.05f)) // 几乎同 a1/a3 方向
        val pred = EmotionPersonalizer.predict(target, listOf(a1, a2, a3))
        assertNotNull(pred)
        // 应偏向冷锚点
        assertEquals(true, pred!!.userValence!! < 0f)
        assertEquals(true, pred.userArousal!! < 0.5f)
    }

    @Test
    fun `skips already user-corrected target`() {
        val a1 = emo(1, emb(1f, 0f), uv = -0.8f, ua = -0.4f)
        val a2 = emo(2, emb(0f, 1f), uv = 0.8f, ua = 0.8f)
        val corrected = emo(9, emb(0.5f, 0.5f), uv = 0.1f, ua = 0.2f)
        assertNull(EmotionPersonalizer.predict(corrected, listOf(a1, a2)))
    }

    @Test
    fun `needs at least 2 usable anchors`() {
        val only = emo(1, emb(1f, 0f), uv = -0.8f, ua = -0.4f)
        val target = emo(9, emb(0.5f, 0.5f))
        assertNull(EmotionPersonalizer.predict(target, listOf(only)))
        assertNull(EmotionPersonalizer.predict(target, emptyList()))
    }

    @Test
    fun `no embedding on target returns null`() {
        val a1 = emo(1, emb(1f, 0f), uv = -0.8f, ua = -0.4f)
        val a2 = emo(2, emb(0f, 1f), uv = 0.8f, ua = 0.8f)
        assertNull(EmotionPersonalizer.predict(emo(9, null), listOf(a1, a2)))
    }

    @Test
    fun `anchor without embedding or correction is ignored`() {
        val good1 = emo(1, emb(1f, 0f), uv = -0.8f, ua = -0.4f)
        val good2 = emo(2, emb(0.8f, 0.2f), uv = -0.6f, ua = -0.2f)
        val noEmb = emo(3, null, uv = 0.9f, ua = 0.9f)
        val noCorr = emo(4, emb(0f, 1f))
        val target = emo(9, emb(0.9f, 0.1f))
        val pred = EmotionPersonalizer.predict(
            target, listOf(good1, good2, noEmb, noCorr)
        )
        assertNotNull(pred)
        assertEquals(true, pred!!.userValence!! < 0f)
    }
}
