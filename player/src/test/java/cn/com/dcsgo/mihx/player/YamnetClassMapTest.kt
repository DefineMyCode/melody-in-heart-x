package cn.com.dcsgo.mihx.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * YAMNet 类别索引表与人声分数计算（2026-10-25）。
 *
 * 这张表是**从 AudioSet 官方 yamnet_class_map.csv 生成**的，索引即模型输出序号。
 * 索引错了 → 人声分数会指向别的声音类别 → 条件标定会用错变量，静默出错。
 * 所以这里锁两件事：索引不越界、计算口径正确。
 */
class YamnetClassMapTest {

    /** emotion_yamnet.tflite 的类别输出维度（Identity_1[2,521]） */
    private val classCount = 521

    @Test
    fun `vocal indices stay inside the model output range`() {
        for (i in YamnetClassMap.VOCAL_INDICES) {
            assertTrue("人声类索引 $i 越界(模型只有 $classCount 类)", i in 0 until classCount)
        }
        for (i in YamnetClassMap.INSTRUMENT_INDICES) {
            assertTrue("器乐类索引 $i 越界(模型只有 $classCount 类)", i in 0 until classCount)
        }
        assertTrue(YamnetClassMap.MUSIC_INDEX in 0 until classCount)
        assertTrue(YamnetClassMap.SILENCE_INDEX in 0 until classCount)
    }

    @Test
    fun `vocal index set is the audited one`() {
        // 人工审过的集合: 说话/歌唱/呼喊三类, 共 11 个。
        // 刻意排除: 鸟叫(107)、鲸叫(131)、颂钵(209)、广义"人声音乐"(249)。
        assertEquals(
            setOf(0, 1, 2, 3, 4, 5, 24, 27, 29, 30, 65),
            YamnetClassMap.VOCAL_INDICES.toSet(),
        )
        // 器乐集合里不能混进曲风类: "Drum and bass"(238) 是 DnB 曲风不是乐器
        assertTrue(238 !in YamnetClassMap.INSTRUMENT_INDICES)
    }

    @Test
    fun `vocal score sums only the vocal classes`() {
        val scores = FloatArray(classCount)
        // 只在人声类 24(Singing) 上给 0.5
        scores[24] = 0.5f
        assertEquals(0.5f, YamnetClassMap.vocalScoreOf(scores), 1e-6f)

        // 多个类相加
        scores[0] = 0.2f
        scores[2] = 0.3f
        assertEquals(1.0f, YamnetClassMap.vocalScoreOf(scores), 1e-6f)

        // 器乐类上的高分**不计入**人声
        val instrumental = FloatArray(classCount)
        instrumental[148] = 1f // Piano
        instrumental[157] = 1f // Drum kit
        assertEquals(0f, YamnetClassMap.vocalScoreOf(instrumental), 1e-6f)
    }

    @Test
    fun `vocal score is zero for a silent frame`() {
        assertEquals(0f, YamnetClassMap.vocalScoreOf(FloatArray(classCount)), 1e-6f)
    }
}
