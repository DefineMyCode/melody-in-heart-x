package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.EmotionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorePlaybackPolicyTest {

    @Test
    fun isSkipped_whenPlayedUnder30Seconds_evenLongSong() {
        // 4 分钟长歌只听了 20 秒 → 跳过
        assertTrue(ExplorePlaybackPolicy.isSkipped(playedMs = 20_000L, durationMs = 240_000L))
    }

    @Test
    fun isSkipped_whenPlayedUnderQuarterDuration_evenOver30Seconds() {
        // 8 分钟长歌听了 60 秒（>30s 但 <1/4=120s）→ 跳过
        assertTrue(ExplorePlaybackPolicy.isSkipped(playedMs = 60_000L, durationMs = 480_000L))
    }

    @Test
    fun isNotSkipped_whenPlayedOver30sAndOverQuarter() {
        // 4 分钟长歌听了 70 秒（>30s 且 >1/4=60s）→ 完整收听（喜欢）
        assertFalse(ExplorePlaybackPolicy.isSkipped(playedMs = 70_000L, durationMs = 240_000L))
    }

    @Test
    fun isSkipped_whenDurationUnknownAndUnder30s() {
        // 总时长未知（<=0），只按 30s 规则
        assertTrue(ExplorePlaybackPolicy.isSkipped(playedMs = 10_000L, durationMs = 0L))
    }

    @Test
    fun isNotSkipped_whenDurationUnknownAndOver30s() {
        assertFalse(ExplorePlaybackPolicy.isSkipped(playedMs = 40_000L, durationMs = 0L))
    }

    @Test
    fun isSkipped_shortSong_playedFullyUnder30s() {
        // 20 秒的短歌听完整首（20s）仍不足 30s → 按规则记「快速跳过」
        assertTrue(ExplorePlaybackPolicy.isSkipped(playedMs = 20_000L, durationMs = 20_000L))
    }

    @Test
    fun `vote weight scales with the word share`() {
        // 票重 = 占比 × 100：主情绪票重高、顺带蹭到的次要词票重低
        assertEquals(80, ExplorePlaybackPolicy.voteWeightOf(0.80f))
        assertEquals(20, ExplorePlaybackPolicy.voteWeightOf(0.20f))
        assertEquals(100, ExplorePlaybackPolicy.voteWeightOf(1.00f))
        // 占比极低 -> 0 票（等于不投：那不是"这个情绪"，只是碰巧落在附近）
        assertEquals(0, ExplorePlaybackPolicy.voteWeightOf(0.004f))
        assertEquals(0, ExplorePlaybackPolicy.voteWeightOf(0f))
    }

    @Test
    fun `vote gate is the same as the display gate`() {
        // 刻意对齐：用户在详情页看到几个标签，跳过时就只否定那几个。
        // 直接引用展示层的常量，两者不可能漂移 —— 这条测试就是那个保证。
        assertEquals(
            EmotionGroup.MIN_RATIO,
            ExplorePlaybackPolicy.VOTE_MIN_RATIO,
            0f,
        )
        assertEquals(
            EmotionGroup.MAX_TAGS,
            ExplorePlaybackPolicy.VOTE_MAX_TAGS,
        )
    }

    @Test
    fun `votesOf drops the words with zero weight`() {
        val votes = ExplorePlaybackPolicy.votesOf(
            mapOf("emo" to 0.50f, "治愈" to 0.20f, "躁" to 0.004f, "燃" to 0f)
        )

        assertEquals(setOf("emo", "治愈"), votes.keys)
        assertEquals(50, votes.getValue("emo"))
        assertEquals(20, votes.getValue("治愈"))
    }
}
