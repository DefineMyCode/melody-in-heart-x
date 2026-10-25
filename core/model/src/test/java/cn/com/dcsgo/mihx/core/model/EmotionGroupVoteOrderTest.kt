package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪投票的**词条顺序契约**（2026-10-25）。
 *
 * 票数是整数，所以比例并列很常见 —— 全库 1095 首里 92 首（8.4%）的展示
 * 集合存在比例并列，其中 17 首的 top1 也并列（并列会决定标题词）。
 *
 * 之前 [EmotionGroup.categoriesFor] 只按占比降序排，并列时的先后取决于
 * votes 这个 HashMap 的迭代序 —— 而 HashMap 的迭代序取决于枚举的
 * identity hash，契约上**不保证**跨进程一致。用户能看见词条顺序，
 * 顺序就不能是"碰运气"的。
 *
 * 现在的契约：**先按占比降序，并列时按组声明序（[EmotionGroup.ordinal]）升序**。
 * 组声明序不随锚点表变，所以重标定锚点、切换坐标系都不影响并列时的先后。
 */
class EmotionGroupVoteOrderTest {

    private val current = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)
    private val legacy = EmotionGroup.anchorsFor("yamnet-va-v3")

    @Test
    fun `tied votes fall back to group declaration order`() {
        // 一窗正好落在 BRIGHT 锚点, 一窗正好落在 EPIC 锚点 -> 两组各 1 票, 精确并列
        val curve = listOf(
            current.getValue(EmotionGroup.BRIGHT),
            current.getValue(EmotionGroup.EPIC),
        )
        val top = EmotionGroup.categoriesFor(curve, current)
        assertEquals(2, top.size)
        assertEquals(0.5f, top[0].second, 1e-6f)
        assertEquals(0.5f, top[1].second, 1e-6f)
        // BRIGHT.ordinal(0) < EPIC.ordinal(2) -> BRIGHT 必须在前
        assertEquals(EmotionGroup.BRIGHT, top[0].first)
        assertEquals(EmotionGroup.EPIC, top[1].first)
    }

    @Test
    fun `vote order is reproducible across repeated calls`() {
        val curve = List(9) { current.getValue(EmotionGroup.BRIGHT) } +
            List(9) { current.getValue(EmotionGroup.EPIC) } +
            List(3) { current.getValue(EmotionGroup.STILL) }
        val first = EmotionGroup.categoriesFor(curve, current)
        repeat(50) {
            assertEquals(first, EmotionGroup.categoriesFor(curve, current))
        }
    }

    @Test
    fun `tie order does not change with the anchor table`() {
        // 同一对组在两套坐标系下都并列 -> 先后必须一致(组声明序与坐标系无关)
        val curveCurrent = listOf(
            current.getValue(EmotionGroup.BRIGHT),
            current.getValue(EmotionGroup.EPIC),
        )
        val curveLegacy = listOf(
            legacy.getValue(EmotionGroup.BRIGHT),
            legacy.getValue(EmotionGroup.EPIC),
        )
        val topCurrent = EmotionGroup.categoriesFor(curveCurrent, current).map { it.first }
        val topLegacy = EmotionGroup.categoriesFor(curveLegacy, legacy).map { it.first }
        assertEquals(listOf(EmotionGroup.BRIGHT, EmotionGroup.EPIC), topCurrent)
        assertEquals(topCurrent, topLegacy)
    }

    @Test
    fun `ratio still outranks declaration order`() {
        // 3 窗 BRIGHT vs 1 窗 EPIC -> BRIGHT 占比更高, 必须排第一(声明序只是并列兜底)
        val curve = List(3) { current.getValue(EmotionGroup.BRIGHT) } +
            List(1) { current.getValue(EmotionGroup.EPIC) }
        val top = EmotionGroup.categoriesFor(curve, current)
        assertEquals(EmotionGroup.BRIGHT, top[0].first)
        assertEquals(EmotionGroup.EPIC, top[1].first)
        assertTrue(top[0].second > top[1].second)
    }
}
