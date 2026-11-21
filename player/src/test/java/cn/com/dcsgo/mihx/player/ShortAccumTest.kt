package cn.com.dcsgo.mihx.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * m-12（评审）回归测试：ShortAccum 的写入窗口必须严格对齐 [maxCapacity]。
 *
 * 旧实现把初始容量下限抬到 1024（coerceAtLeast(1024)），当 maxCapacity < 1024 时
 * 初始数组就已超过 maxCapacity，而 add 仅在 size == data.size 时才检查上限，
 * 导致可写入超过 maxCapacity 的样本，越界写入。
 */
class ShortAccumTest {

    @Test
    fun addStopsAtMaxCapacityWhenMaxCapacityBelowInitialCapacity() {
        // maxCapacity = 10，远小于默认 initialCapacity（SR*30 = 480000）
        val accum = ShortAccum(initialCapacity = 100, maxCapacity = 10)

        // 写入 10 个样本——正好到上限，不应截断
        repeat(10) { accum.add(1) }
        assertEquals(10, accum.size)
        assertFalse("写入 10 个（= maxCapacity）不应截断", accum.truncated)

        // 第 11 个必须被截断——旧实现此处会越界写入
        accum.add(1)
        assertEquals("第 11 个样本必须被截断，size 不应超过 maxCapacity", 10, accum.size)
        assertTrue("超过 maxCapacity 后必须标记 truncated", accum.truncated)
    }

    @Test
    fun initialCapacityIsCoercedToMaxCapacityWhenInitialExceedsMax() {
        // maxCapacity = 5，initialCapacity = 100：初始数组容量应被压到 5
        val accum = ShortAccum(initialCapacity = 100, maxCapacity = 5)
        assertEquals("初始容量不得超过 maxCapacity", 5, accum.data.size)

        repeat(5) { accum.add(1) }
        assertEquals(5, accum.size)
        assertFalse(accum.truncated)

        accum.add(1)
        assertEquals(5, accum.size)
        assertTrue(accum.truncated)
    }

    @Test
    fun initialCapacityIsCoercedToOneWhenMaxCapacityIsOne() {
        // 边界：maxCapacity = 1
        val accum = ShortAccum(initialCapacity = 100, maxCapacity = 1)
        assertEquals(1, accum.data.size)

        accum.add(1)
        assertEquals(1, accum.size)
        assertFalse(accum.truncated)

        accum.add(1)
        assertEquals(1, accum.size)
        assertTrue(accum.truncated)
    }

    @Test
    fun addGrowsCapacityAndStopsAtMaxCapacityWhenMaxExceedsInitial() {
        // maxCapacity = 20，initialCapacity = 10：验证倍增扩容路径下仍不超过 maxCapacity
        val accum = ShortAccum(initialCapacity = 10, maxCapacity = 20)

        repeat(10) { accum.add(1) }
        assertEquals(10, accum.size)
        // size == data.size == 10，下次 add 才触发扩容
        assertEquals(10, accum.data.size)

        // 第 11 个 add 触发倍增扩容到 min(20, 20) = 20
        accum.add(1)
        assertEquals(11, accum.size)
        assertEquals(20, accum.data.size)

        // 再写 9 个到上限 20
        repeat(9) { accum.add(1) }
        assertEquals(20, accum.size)
        assertFalse(accum.truncated)

        accum.add(1)
        assertEquals(20, accum.size)
        assertTrue(accum.truncated)
    }
}
