package cn.com.dcsgo.mihx.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MoodPoolStatsTest {

    @Test
    fun `counts excluded matched and available`() {
        val ratiosBySong = mapOf(
            1 to mapOf("静谧" to 1f),
            2 to mapOf("静谧" to 1f, "躁" to 0.27f),
            3 to mapOf("静谧" to 0.20f), // 未达到包含门槛 35%
            4 to mapOf("躁" to 0.27f),
            5 to mapOf("其他" to 0.50f),
        )

        // 曲库 10 首：5 首已分析 + 5 首未分析（未分析无词条，可进入合计池）
        val stats = MoodPoolStats.from(
            ratiosBySong = ratiosBySong,
            tags = setOf("静谧"),
            excludedTags = setOf("躁"),
            librarySize = 10,
        )

        assertEquals(2, stats.excluded)   // 歌 2、歌 4
        assertEquals(1, stats.matched)    // 排除后只剩歌 1
        assertEquals(8, stats.available)  // 10 - 2
    }

    @Test
    fun `blank included tags still counts exclusions`() {
        val stats = MoodPoolStats.from(
            ratiosBySong = mapOf(
                1 to mapOf("静谧" to 1f),
                2 to mapOf("躁" to 0.27f),
            ),
            tags = emptySet(),
            excludedTags = setOf("躁"),
            librarySize = 10,
        )

        assertEquals(1, stats.excluded)
        assertEquals(0, stats.matched)
        assertEquals(9, stats.available)
    }
}
