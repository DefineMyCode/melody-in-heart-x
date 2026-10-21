package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.domain.repository.EmotionFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪分析解码熔断的「超长」与「内存超限」区分（2026-10-21）。
 *
 * 回归：192kHz 采样率 / 大文件、但时长未超 20 分钟的歌，旧实现被误报
 * 「歌曲超过 20 分钟」——实际是内存预算先于时长上限命中。
 * 内存预算自适应设备堆上限：[memoryBudgetForHeap]。
 */
class EmotionAnalyzerLimitsTest {

    /** 256MB 堆对应的预算（与旧固定值 192MB 一致） */
    private val budget256 = EmotionAnalyzer.memoryBudgetForHeap(256L * 1024 * 1024)

    @Test
    fun lowSampleRateIsBoundByDurationNotMemory() {
        // 44.1k / 48k：20 分钟时长上限先命中（内存预算后到）→ 上限 = 20分钟 × 采样率
        assertEquals(EmotionAnalyzer.MAX_SECONDS * 44100L, EmotionAnalyzer.maxSamplesForSrc(44100, budget256))
        assertEquals(EmotionAnalyzer.MAX_SECONDS * 48000L, EmotionAnalyzer.maxSamplesForSrc(48000, budget256))
    }

    @Test
    fun highSampleRateIsBoundByMemoryBeforeDuration() {
        // 96k / 192k：内存预算先命中 → 20 分钟上限形同虚设
        assertTrue(EmotionAnalyzer.maxSamplesForSrc(96_000, budget256) < EmotionAnalyzer.MAX_SECONDS * 96_000L)
        assertTrue(EmotionAnalyzer.maxSamplesForSrc(192_000, budget256) < EmotionAnalyzer.MAX_SECONDS * 192_000L)
    }

    @Test
    fun cutoffReasonDistinguishesDurationVsMemory() {
        // 192kHz 10 分钟（未超 20 分钟）→ 内存超限（本次 bug 场景）
        assertEquals(
            EmotionFailureReason.TOO_LARGE_FOR_DEVICE,
            EmotionAnalyzer.samplesCutoffReason(192_000, 600L * 192_000L, budget256),
        )
        // 192kHz 25 分钟 → 超长
        assertEquals(
            EmotionFailureReason.TOO_LONG,
            EmotionAnalyzer.samplesCutoffReason(192_000, 1500L * 192_000L, budget256),
        )
        // 44.1k 10 分钟 → 不超限
        assertNull(EmotionAnalyzer.samplesCutoffReason(44_100, 600L * 44_100L, budget256))
        // 44.1k 25 分钟 → 超长
        assertEquals(
            EmotionFailureReason.TOO_LONG,
            EmotionAnalyzer.samplesCutoffReason(44_100, 1500L * 44_100L, budget256),
        )
    }

    @Test
    fun truncationReasonMatchesTighterLimit() {
        // 44.1k：时长上限更紧 → 解码中截断判定为超长
        assertEquals(EmotionFailureReason.TOO_LONG, EmotionAnalyzer.truncationReason(44_100, budget256))
        // 192k：内存上限更紧 → 解码中截断判定为内存超限
        assertEquals(EmotionFailureReason.TOO_LARGE_FOR_DEVICE, EmotionAnalyzer.truncationReason(192_000, budget256))
    }

    @Test
    fun memoryBudgetScalesWithHeapAndClamps() {
        fun mb(v: Long) = v * 1024 * 1024
        // 256MB 堆 → 192MB（与旧固定值一致）
        assertEquals(mb(192), EmotionAnalyzer.memoryBudgetForHeap(mb(256)))
        // 384MB 堆 → 320MB；512MB 堆 → 448MB（自动放宽）
        assertEquals(mb(320), EmotionAnalyzer.memoryBudgetForHeap(mb(384)))
        assertEquals(mb(448), EmotionAnalyzer.memoryBudgetForHeap(mb(512)))
        // 过小的堆 → 收敛到下限 96MB
        assertEquals(mb(96), EmotionAnalyzer.memoryBudgetForHeap(mb(128)))
        // 过大的堆 → 封顶 512MB
        assertEquals(mb(512), EmotionAnalyzer.memoryBudgetForHeap(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun largerHeapLetsLongerHighSampleRateSongsPass() {
        // 同一首 192kHz 10 分钟的歌：
        // 256MB 堆（预算 192MB）→ 内存超限
        assertEquals(
            EmotionFailureReason.TOO_LARGE_FOR_DEVICE,
            EmotionAnalyzer.samplesCutoffReason(192_000, 600L * 192_000L, EmotionAnalyzer.memoryBudgetForHeap(mb(256))),
        )
        // 512MB 堆（预算 448MB）→ 通过（不再触发）
        assertNull(
            EmotionAnalyzer.samplesCutoffReason(192_000, 600L * 192_000L, EmotionAnalyzer.memoryBudgetForHeap(mb(512))),
        )
    }

    private fun mb(v: Long) = v * 1024 * 1024
}
