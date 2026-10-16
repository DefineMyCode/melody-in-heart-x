package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-1（评审 2026-10-15）回归测试：切歌不得泄漏计时循环。
 *
 * 泄漏机理：startPlayback() 先 stopPlayback()（isTracking=false）再 startTracking()
 * （compareAndSet(false,true) 放行新循环），旧循环此刻仍在 delay() 中休眠；
 * 醒来后 while(isTracking.get()) 看到的是新会话拉回的 true，于是继续运行——
 * 每次切歌都会多一个永不退出的计时循环。
 */
class PlayDurationTrackerTest {

    @Test
    fun switchingSongsDoesNotAccumulateTrackingLoops() = runBlocking {
        val tracker = PlayDurationTracker(FakePlayStatsRepository(), FakeQuickSkipRepository(), updateIntervalMs = 10)

        // 连续切歌 5 次：切歌间隔 25ms 与 tick 间隔 10ms 刻意不对齐，
        // 保证旧循环醒来时必然落在新会话（isTracking 已被新循环拉回 true）→ 稳定复现泄漏
        repeat(5) { i ->
            tracker.startPlayback(songId = 100 + i, durationMs = 60_000, initialPlayedMs = 0)
            delay(25)
        }

        try {
            // 旧循环必须在有限时间内退出，存活循环数回到 1（仅当前歌曲的循环）
            withTimeout(2_000) {
                while (tracker.activeTrackingLoops.get() > 1) delay(10)
            }
            assertEquals(1, tracker.activeTrackingLoops.get())
        } finally {
            tracker.stopPlayback()
        }

        // 最终停止后所有计时循环退出
        withTimeout(2_000) {
            while (tracker.activeTrackingLoops.get() > 0) delay(10)
        }
        assertEquals(0, tracker.activeTrackingLoops.get())
    }

    @Test
    fun playbackAccumulatesElapsedRealTimeWhilePlaying() = runBlocking {
        val tracker = PlayDurationTracker(FakePlayStatsRepository(), FakeQuickSkipRepository(), updateIntervalMs = 10)
        try {
            tracker.startPlayback(songId = 1, durationMs = 60_000, initialPlayedMs = 0)
            delay(200)
            tracker.pausePlayback()
            val accumulated = tracker.getCurrentPlayDuration()
            // tick 间隔 10ms：200ms 真实播放应累计出接近值（留出调度抖动余量）
            assertTrue("累计时长应在 150~300ms 之间，实际 $accumulated", accumulated in 150..300)
        } finally {
            tracker.stopPlayback()
        }
    }

    @Test
    fun stopPlaybackSettlesRawPlayCountOncePerSession() = runBlocking {
        val stats = FakePlayStatsRepository()
        val tracker = PlayDurationTracker(stats, FakeQuickSkipRepository(), updateIntervalMs = 10)
        tracker.startPlayback(songId = 7, durationMs = 60_000, initialPlayedMs = 0)
        delay(30)
        tracker.stopPlayback()
        withTimeout(2_000) {
            while (tracker.activeTrackingLoops.get() > 0) delay(10)
        }
        assertEquals(1, stats.rawIncrements[7])
    }
}

/** 只统计调用、不落盘的假仓库（计时逻辑测试不依赖存储） */
private class FakePlayStatsRepository : PlayStatsRepository {
    val rawIncrements = mutableMapOf<Int, Int>()

    override suspend fun getCounts(songIds: List<Int>): Map<Int, Int> = emptyMap()
    override suspend fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int> = emptyMap()
    override suspend fun getRankedCounts(useRawCounts: Boolean, descending: Boolean): List<Pair<Int, Int>> = emptyList()
    override suspend fun getPlayCountsForSort(): Map<Int, Int> = emptyMap()
    override suspend fun getLastPlayedAtForSort(): Map<Int, Long> = emptyMap()
    override suspend fun increment(songId: Int): Int = 0
    override suspend fun incrementRawPlayCount(songId: Int): Int {
        rawIncrements[songId] = (rawIncrements[songId] ?: 0) + 1
        return rawIncrements[songId] ?: 0
    }
    override suspend fun recordCompletedPlay(songId: Int) = Unit
    override suspend fun recordPlaybackSession(songId: Int, startedAtMs: Long, durationMs: Long, isEffectivePlay: Boolean) = Unit
    override suspend fun playbackStatsSnapshot() = throw UnsupportedOperationException()
}

private class FakeQuickSkipRepository : QuickSkipRepository {
    override suspend fun getSongIds(): Set<Int> = emptySet()
    override suspend fun add(songId: Int): Boolean = false
    override suspend fun remove(songId: Int): Boolean = false
    override suspend fun contains(songId: Int): Boolean = false
    override suspend fun incrementShortPlayCount(songId: Int): Int = 0
    override suspend fun resetShortPlayCount(songId: Int) = Unit
}
