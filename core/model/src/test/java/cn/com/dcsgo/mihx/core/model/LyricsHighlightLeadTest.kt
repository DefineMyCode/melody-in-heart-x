package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * getCurrentLineIndex 预滚动补偿回归（2026-09-03 歌词实时性优化）：
 * leadMs 让行在唱到之前即被判定为当前行，补偿滚动动画延迟，
 * 使行"唱到时刚好滚到中央"而非唱过 ~300ms 后才反应。
 */
class LyricsHighlightLeadTest {

    private val lyrics = Lyrics(
        lines = listOf(
            LyricLine(10_000L, "第一句"),
            LyricLine(20_000L, "第二句"),
            LyricLine(30_000L, "第三句"),
        ),
    )

    @Test
    fun `default lead highlights upcoming line 300ms early`() {
        // 19_800ms：距第二句还有 200ms，默认提前量下已判定为当前行（滚动先行）
        assertEquals(1, lyrics.getCurrentLineIndex(19_800L))
        // 无提前量时仍是第一句
        assertEquals(0, lyrics.getCurrentLineIndex(19_800L, leadMs = 0L))
    }

    @Test
    fun `zero lead preserves strict timestamp behavior`() {
        assertEquals(0, lyrics.getCurrentLineIndex(19_999L, leadMs = 0L))
        assertEquals(1, lyrics.getCurrentLineIndex(20_000L, leadMs = 0L))
    }

    @Test
    fun `negative lead delays highlight`() {
        assertEquals(1, lyrics.getCurrentLineIndex(20_000L, leadMs = 0L))
        // 20_100ms 但延后 300ms：仍算第一句
        assertEquals(0, lyrics.getCurrentLineIndex(20_100L, leadMs = -300L))
    }

    @Test
    fun `before first line returns minus one`() {
        assertEquals(-1, lyrics.getCurrentLineIndex(0L))
        // 无提前量：第一行 timeMs 之前一律 -1
        assertEquals(-1, lyrics.getCurrentLineIndex(9_999L, leadMs = 0L))
        // 有提前量：距第一行 300ms 时即开始高亮（提前量的语义本就如此）
        assertEquals(0, lyrics.getCurrentLineIndex(9_700L, leadMs = Lyrics.HIGHLIGHT_LEAD_MS))
    }

    @Test
    fun `empty lyrics returns minus one`() {
        assertEquals(-1, Lyrics.EMPTY.getCurrentLineIndex(10_000L))
    }
}
