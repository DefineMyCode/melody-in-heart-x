package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MoodSlotResolver 全边角单测（设计文档 §4.4）：
 * 时段判定（含跨午夜 / 左闭右开边界 / 互斥）、配置校验（重叠 / 零长度 / 空词条）、开关语义。
 */
class MoodSlotResolverTest {

    private val resolver = MoodSlotResolver()

    private fun slot(
        id: Long,
        start: Int,
        end: Int,
        vararg tags: String,
    ) = TimeSlotConfig(
        id = id,
        name = "S$id",
        startMinutes = start,
        endMinutes = end,
        tags = tags.toList(),
    )

    // ── 时段判定：普通区间 ──

    @Test
    fun `normal slot hits inside and misses boundaries per half-open semantics`() {
        val configs = listOf(slot(1, start = 420, end = 570, "元气")) // 07:00–09:30
        assertEquals(setOf("元气"), resolver.activeTags(configs, true, 420))   // 开始整点命中
        assertEquals(setOf("元气"), resolver.activeTags(configs, true, 569))   // 结束前 1 分钟命中
        assertNull(resolver.activeTags(configs, true, 570))                    // 结束整点不命中
        assertNull(resolver.activeTags(configs, true, 419))                    // 开始前不命中
    }

    // ── 时段判定：跨午夜 ──

    @Test
    fun `overnight slot hits late night early morning and midnight`() {
        val configs = listOf(slot(1, start = 22 * 60, end = 6 * 60, "静谧")) // 22:00–06:00
        assertEquals(setOf("静谧"), resolver.activeTags(configs, true, 22 * 60))        // 22:00 命中
        assertEquals(setOf("静谧"), resolver.activeTags(configs, true, 23 * 60 + 41))   // 23:41 命中
        assertEquals(setOf("静谧"), resolver.activeTags(configs, true, 0))              // 00:00 命中
        assertEquals(setOf("静谧"), resolver.activeTags(configs, true, 5 * 60 + 59))    // 05:59 命中
        assertNull(resolver.activeTags(configs, true, 6 * 60))                          // 06:00 不命中
        assertNull(resolver.activeTags(configs, true, 21 * 60 + 59))                    // 21:59 不命中
        assertNull(resolver.activeTags(configs, true, 12 * 60))                         // 正午不命中
    }

    // ── 时段判定：互斥与确定性 ──

    @Test
    fun `exactly one slot hits at any minute when configs are valid`() {
        val configs = listOf(
            slot(1, 0, 6 * 60, "静谧"),
            slot(2, 6 * 60, 9 * 60, "元气"),
            slot(3, 9 * 60, 18 * 60, "放松"),
            slot(4, 18 * 60, 24 * 60, "燃"),
        )
        // 全天逐分钟扫一遍：任何时刻至多命中一个
        for (minute in 0 until TimeSlotConfig.MINUTES_PER_DAY) {
            val hits = configs.filter { it.covers(minute) }
            assertTrue("minute=$minute hits=${hits.size}", hits.size <= 1)
            val active = resolver.activeTags(configs, true, minute)
            if (hits.isEmpty()) {
                assertNull("minute=$minute", active)
            } else {
                assertEquals("minute=$minute", hits.single().tags.toSet(), active)
            }
        }
    }

    @Test
    fun `multiple hits on invalid data resolve deterministically to earliest start`() {
        val configs = listOf(
            slot(2, start = 12 * 60, end = 18 * 60, "B"),
            slot(1, start = 10 * 60, end = 20 * 60, "A"),
        )
        assertEquals(setOf("A"), resolver.activeTags(configs, true, 13 * 60))
        assertEquals(1L, resolver.hitSlot(configs, 13 * 60)?.id)
    }

    // ── 开关语义 ──

    @Test
    fun `disabled switch never activates even with hit`() {
        val configs = listOf(slot(1, 0, 24 * 60, "静谧"))
        assertNull(resolver.activeTags(configs, enabled = false, minuteOfDay = 100))
        assertEquals(setOf("静谧"), resolver.activeTags(configs, enabled = true, minuteOfDay = 100))
    }

    @Test
    fun `empty configs never activate`() {
        assertNull(resolver.activeTags(emptyList(), true, 100))
    }

    // ── 校验：名称 ──

    @Test
    fun `validate rejects blank and overlong names`() {
        val base = slot(1, 0, 60, "静谧")
        assertTrue(resolver.validate(base.copy(name = "  "), emptyList())
            is SlotValidation.Invalid)
        val error = resolver.validate(base.copy(name = "x".repeat(21)), emptyList())
        assertEquals(SlotError.NAME_TOO_LONG, (error as SlotValidation.Invalid).error)
        assertEquals(
            SlotValidation.Valid,
            resolver.validate(base.copy(name = "x".repeat(20)), emptyList()),
        )
    }

    // ── 校验：词条 / 零长度 ──

    @Test
    fun `validate rejects empty tags and zero length`() {
        val base = slot(1, 0, 60, "静谧")
        assertEquals(
            SlotError.NO_TAGS,
            (resolver.validate(base.copy(tags = emptyList()), emptyList()) as SlotValidation.Invalid).error,
        )
        assertEquals(
            SlotError.ZERO_LENGTH,
            (resolver.validate(base.copy(endMinutes = 0), emptyList()) as SlotValidation.Invalid).error,
        )
    }

    // ── 校验：重叠 ──

    @Test
    fun `validate rejects overlap with existing slot`() {
        val existing = slot(1, 22 * 60, 6 * 60, "静谧") // 22:00–06:00 跨午夜
        val candidate = slot(2, 20 * 60, 23 * 60 + 30, "放松") // 20:00–23:30 与其重叠
        val result = resolver.validate(candidate, listOf(existing))
        assertTrue(result is SlotValidation.Conflict)
        assertEquals(1L, (result as SlotValidation.Conflict).conflicting.id)
    }

    @Test
    fun `validate allows touching slots per half-open semantics`() {
        val existing = slot(1, 6 * 60, 9 * 60, "元气") // 06:00–09:00
        val candidate = slot(2, 9 * 60, 12 * 60, "放松") // 09:00–12:00 端点相接不重叠
        assertEquals(SlotValidation.Valid, resolver.validate(candidate, listOf(existing)))
    }

    @Test
    fun `validate allows editing same id without self conflict`() {
        val existing = slot(1, 6 * 60, 9 * 60, "元气")
        // 同 id 的时间段扩展也不算自我冲突
        val edited = slot(1, 5 * 60, 10 * 60, "元气")
        assertEquals(SlotValidation.Valid, resolver.validate(edited, listOf(existing)))
    }

    // ── overlaps：环形时间轴直接验证 ──

    @Test
    fun `overnight overlaps morning segment after midnight`() {
        val overnight = slot(1, 22 * 60, 6 * 60)
        val morning = slot(2, 0, 8 * 60)
        assertTrue(resolver.overlaps(overnight, morning))
        // 对称性
        assertTrue(resolver.overlaps(morning, overnight))
    }

    @Test
    fun `overnight does not overlap midday`() {
        val overnight = slot(1, 22 * 60, 6 * 60)
        val midday = slot(2, 10 * 60, 16 * 60)
        assertFalse(resolver.overlaps(overnight, midday))
    }

    @Test
    fun `two overnight slots always overlap`() {
        val a = slot(1, 22 * 60, 6 * 60)
        val b = slot(2, 23 * 60, 2 * 60)
        assertTrue(resolver.overlaps(a, b))
    }

    @Test
    fun `full day slot overlaps everything non-zero`() {
        val full = slot(1, 0, 24 * 60)
        val any = slot(2, 10 * 60, 11 * 60)
        assertTrue(resolver.overlaps(full, any))
    }
}
