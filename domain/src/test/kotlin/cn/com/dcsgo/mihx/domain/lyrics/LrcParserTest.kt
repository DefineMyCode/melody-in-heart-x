package cn.com.dcsgo.mihx.domain.lyrics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LrcParserTest {

    @Test
    fun `parses standard timestamp and text`() {
        val lines = LrcParser.parse("[00:12.34]第一句")
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].timeMs)
        assertEquals("第一句", lines[0].text)
    }

    @Test
    fun `one line with repeated timestamps emits one entry per timestamp`() {
        val lines = LrcParser.parse("[00:01.00][00:05.00]重复句")
        assertEquals(2, lines.size)
        assertEquals(listOf(1_000L, 5_000L), lines.map { it.timeMs })
        assertEquals(listOf("重复句", "重复句"), lines.map { it.text })
    }

    @Test
    fun `metadata tags and offset are ignored`() {
        val text = "[ti:歌名]\n[ar:歌手]\n[offset:500]\n[00:10.00]正片"
        val lines = LrcParser.parse(text)
        assertEquals(1, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
    }

    @Test
    fun `plain text without timestamps is dropped`() {
        assertTrue(LrcParser.parse("没有时间戳的一行").isEmpty())
    }

    @Test
    fun `empty lines and empty content are dropped`() {
        assertTrue(LrcParser.parse("\n\n[00:01.00]\n").isEmpty())
    }

    @Test
    fun `output is sorted by time even with unsorted input`() {
        val lines = LrcParser.parse("[00:03.00]c\n[00:01.00]a\n[00:02.00]b")
        assertEquals(listOf("a", "b", "c"), lines.map { it.text })
    }

    @Test
    fun `minutes carry into milliseconds`() {
        assertEquals(90_000L, LrcParser.parse("[01:30.00]长句").single().timeMs)
    }

    @Test
    fun `fractional digits of any width normalize to milliseconds`() {
        assertEquals(500L, LrcParser.parse("[00:00.5]a").single().timeMs)
        assertEquals(500L, LrcParser.parse("[00:00.50]a").single().timeMs)
        assertEquals(500L, LrcParser.parse("[00:00.500]a").single().timeMs)
        assertEquals(50L, LrcParser.parse("[00:00.05]a").single().timeMs)
    }

    @Test
    fun `colon separated fraction is accepted`() {
        assertEquals(1_500L, LrcParser.parse("[00:01:50]a").single().timeMs)
    }

    @Test
    fun `timestamp without fraction defaults to zero ms`() {
        assertEquals(60_000L, LrcParser.parse("[01:00]a").single().timeMs)
    }
}
