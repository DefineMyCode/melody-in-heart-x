package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪模型规格（坐标系版本）的**一致性契约**（2026-10-25）。
 *
 * 背景：情绪坐标系版本原来是一个手写字符串 "yamnet-va-v4-rms"，而"要不要重扫"
 * 整个判定就挂在这个字符串上 —— 换了模型资产却忘了改它，老数据会静默留存、
 * 永远不重扫（评审 P2）。
 *
 * 现在版本串是 "<坐标系标签>@<内容指纹>"，两部分各司其职：
 *  - 指纹：由 core/model/emotion_model_spec.properties + 两个 tflite 资产算出，
 *    构建期校验资产哈希（换资产忘改清单 → 构建直接失败）；
 *  - 标签：决定用哪套锚点表（必须与曲线自己的坐标系一致）。
 */
class EmotionModelSpecTest {

    @Test
    fun `model version carries label and content fingerprint`() {
        val version = EmotionGroup.CURRENT_MODEL_VERSION
        assertTrue(
            "版本串应形如 <label>@<fingerprint>, 实际 $version",
            version.startsWith("${EmotionGroup.CURRENT_MODEL_LABEL}@"),
        )
        assertEquals(EmotionModelSpec.MODEL_VERSION, version)
        assertEquals(
            "${EmotionGroup.CURRENT_MODEL_LABEL}@${EmotionModelSpec.FINGERPRINT}",
            version,
        )
    }

    @Test
    fun `anchors are calibrated for the current coordinate parameters`() {
        // 改了 emotion_model_spec.properties 里的 A/B 参数却没重标定锚点表 ->
        // 这里会红(参数指纹变了, 但锚点表还是按旧参数标的)。
        assertEquals(
            "锚点表是按旧参数标定的: 改了坐标参数必须重标定锚点表, " +
                "并把 EmotionGroup.ANCHORS_CALIBRATED_FOR 更新成新的参数指纹",
            EmotionModelSpec.PARAMS_FINGERPRINT,
            EmotionGroup.ANCHORS_CALIBRATED_FOR,
        )
    }

    @Test
    fun `anchor table is chosen by coordinate label not by fingerprint`() {
        val current = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
        val legacy = EmotionGroup.anchorsFor(EmotionGroup.LEGACY_MODEL_LABEL)

        // 标签相同 -> 同一套表(指纹不同也一样: 指纹不参与选表)
        assertEquals(
            current,
            EmotionGroup.anchorsFor("${EmotionGroup.CURRENT_MODEL_LABEL}@deadbeef"),
        )
        // 旧标签 -> 旧表
        assertEquals(legacy, EmotionGroup.anchorsFor("yamnet-va-v3"))
        assertTrue("新旧坐标系必须用不同的锚点表", current != legacy)
        // 未登记的标签 -> 旧表(旧曲线占绝大多数)
        assertEquals(legacy, EmotionGroup.anchorsFor("some-unknown-model"))
    }

    @Test
    fun `current label resolves to the enum anchor table`() {
        val table = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
        assertEquals(
            EmotionGroup.BRIGHT.anchorV to EmotionGroup.BRIGHT.anchorA,
            table.getValue(EmotionGroup.BRIGHT),
        )
        assertEquals(
            EmotionGroup.RAGE.anchorV to EmotionGroup.RAGE.anchorA,
            table.getValue(EmotionGroup.RAGE),
        )
        assertEquals(
            EmotionGroup.ABYSS.anchorV to EmotionGroup.ABYSS.anchorA,
            table.getValue(EmotionGroup.ABYSS),
        )
    }
}
