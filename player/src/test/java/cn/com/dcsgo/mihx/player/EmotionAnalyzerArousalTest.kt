package cn.com.dcsgo.mihx.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * arousal 响度包络 + valence 标定（2026-10-24，方案 A/B）。
 *
 * 依据 docs/architecture/EMOTION_VA_UPGRADE.md：
 * - A：arousal 不再取 VA 头的输出（那是"曲风代理"），改为窗内 RMS 的物理映射；
 * - B：valence 做零点/斜率标定，抵消模型输出的系统性正偏。
 *
 * 这里的契约决定「燃」「静谧」这类绝对能量词条够不够得着——
 * 映射区间两端必须严格可达，所以边界值要钉死。
 */
class EmotionAnalyzerArousalTest {

    private val win = EmotionAnalyzer.WINDOW_SAMPLES

    @Test
    fun `db floor maps to minus one and db ceil maps to plus one`() {
        assertEquals(-1f, EmotionAnalyzer.dbToArousal(EmotionAnalyzer.DB_FLOOR), 1e-6f)
        assertEquals(1f, EmotionAnalyzer.dbToArousal(EmotionAnalyzer.DB_CEIL), 1e-6f)
    }

    @Test
    fun `midpoint maps to zero`() {
        val mid = (EmotionAnalyzer.DB_FLOOR + EmotionAnalyzer.DB_CEIL) / 2.0
        assertEquals(0f, EmotionAnalyzer.dbToArousal(mid), 1e-6f)
    }

    @Test
    fun `arousal is clamped outside the mapping range`() {
        assertEquals(-1f, EmotionAnalyzer.dbToArousal(EmotionAnalyzer.DB_FLOOR - 20.0), 1e-6f)
        assertEquals(1f, EmotionAnalyzer.dbToArousal(EmotionAnalyzer.DB_CEIL + 20.0), 1e-6f)
    }

    @Test
    fun `arousal is non-decreasing with loudness and strictly increasing inside the range`() {
        var prev = -2f
        for (db in -60..20 step 5) {
            val a = EmotionAnalyzer.dbToArousal(db.toDouble())
            assertTrue("db=$db arousal=$a 不应小于上一个 $prev", a >= prev)
            prev = a
        }
        // 区间内部（不含端点）必须严格递增——否则映射失去分辨力
        val inner = (EmotionAnalyzer.DB_FLOOR.toInt() + 1 until EmotionAnalyzer.DB_CEIL.toInt()).toList()
        for (i in 0 until inner.size - 1) {
            val a = EmotionAnalyzer.dbToArousal(inner[i].toDouble())
            val b = EmotionAnalyzer.dbToArousal(inner[i + 1].toDouble())
            assertTrue("db=${inner[i]}→${inner[i + 1]}: $a → $b 应严格递增", b > a)
        }
    }

    @Test
    fun `windowRms of a constant signal equals its amplitude`() {
        val pcm = FloatArray(win) { 0.5f }
        assertEquals(0.5, EmotionAnalyzer.windowRms(pcm, 0, win), 1e-6)
    }

    @Test
    fun `windowRms of silence is zero`() {
        assertEquals(0.0, EmotionAnalyzer.windowRms(FloatArray(win), 0, win), 1e-9)
    }

    @Test
    fun `windowRms only reads the requested segment`() {
        // 前半段静音、后半段满幅：起点不同 -> RMS 不同
        val pcm = FloatArray(win * 2) { 0f }
        for (i in win until win * 2) pcm[i] = 1f
        assertEquals(0.0, EmotionAnalyzer.windowRms(pcm, 0, win), 1e-9)
        assertEquals(1.0, EmotionAnalyzer.windowRms(pcm, win, win), 1e-6)
    }

    @Test
    fun `quiet window maps to negative arousal and loud window to positive`() {
        val quiet = FloatArray(win) { 0.002f }   // ≈ -54 dBFS
        val loud = FloatArray(win) { 0.5f }      // ≈ -6 dBFS
        assertTrue(EmotionAnalyzer.windowArousal(quiet) < 0f)
        assertTrue(EmotionAnalyzer.windowArousal(loud) > 0f)
    }

    @Test
    fun `valence calibration moves the bias to zero`() {
        // B = 0.25 是全库歌级均值，标定后应恰好落在 0
        assertEquals(0f, EmotionAnalyzer.calibrateValence(EmotionAnalyzer.V_CAL_BIAS), 1e-6f)
    }

    @Test
    fun `valence calibration keeps scale and clamps to range`() {
        // (1.0 - 0.25) / 0.85 = 0.88235...
        assertEquals(0.75f / 0.85f, EmotionAnalyzer.calibrateValence(1.0f), 1e-6f)
        // 标定会把最负的 valence 推到 -1 之外，必须夹回坐标系
        assertEquals(-1f, EmotionAnalyzer.calibrateValence(-1f), 1e-6f)
    }
}
