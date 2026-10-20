package cn.com.dcsgo.mihx.domain.playback

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
}
