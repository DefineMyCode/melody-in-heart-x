package cn.com.dcsgo.mihx.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * LRC [offset:] 标签解析回归（2026-09-03 歌词实时性优化）：
 * 此前 offset 被直接丢弃，造成带校准标签的歌词系统性滞后/超前。
 */
class LrcParserOffsetTest {

    @Test
    fun `positive offset shifts lyric timestamps earlier`() {
        // LRC 规范：正值 offset 表示歌词整体提前（timeMs += offset）
        val lyrics = LrcParser.parseLrcContent(
            """
            [ti:Test]
            [offset:500]
            [00:10.00]第一句
            [00:20.00]第二句
            """.trimIndent(),
        )!!

        assertEquals(listOf(10_500L, 20_500L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `negative offset delays lyric timestamps`() {
        val lyrics = LrcParser.parseLrcContent(
            """
            [offset:-300]
            [00:10.00]第一句
            """.trimIndent(),
        )!!

        assertEquals(listOf(9_700L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `missing or invalid offset keeps original timestamps`() {
        val withoutOffset = LrcParser.parseLrcContent("[00:10.00]第一句")!!
        assertEquals(listOf(10_000L), withoutOffset.lines.map { it.timeMs })

        val invalidOffset = LrcParser.parseLrcContent(
            """
            [offset:abc]
            [00:10.00]第一句
            """.trimIndent(),
        )!!
        assertEquals(listOf(10_000L), invalidOffset.lines.map { it.timeMs })
    }

    @Test
    fun `offset applies to every line including multi-timestamp lines`() {
        val lyrics = LrcParser.parseLrcContent(
            """
            [offset:1000]
            [00:01.00][01:02.00]重复句
            """.trimIndent(),
        )!!

        assertEquals(listOf(2_000L, 63_000L), lyrics.lines.map { it.timeMs })
    }

    @Test
    fun `empty content returns null`() {
        assertNull(LrcParser.parseLrcContent("   "))
    }
}
