package cn.com.dcsgo.mihx.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncrementalQueueSyncPlanTest {

    @Test
    fun pureTailAppendUsesIncremental() {
        // refill 补队列：controller 队列是新窗口的前缀，当前项不变 → 增量追加尾部
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3, 4, 5, 6),
            controllerCurrentIndex = 2,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9),
            planStartIndex = 2,
        )
        assertEquals(IncrementalQueueSyncPlan(trimCount = 0, commonCount = 6), plan)
    }

    @Test
    fun windowScrollWithTailAppendUsesIncremental() {
        // 窗口前部滚动（裁掉 1、2）+ 尾部追加（8、9），当前歌 6 保持
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3, 4, 5, 6, 7),
            controllerCurrentIndex = 5,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(3, 4, 5, 6, 7, 8, 9),
            planStartIndex = 3,
        )
        assertEquals(IncrementalQueueSyncPlan(trimCount = 2, commonCount = 5), plan)
    }

    @Test
    fun identicalQueueIsZeroOperation() {
        // 队列完全一致（仅 fingerprint 因 startIndex 漂移而不同）→ 零操作，不触发重建
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2, 3),
            planStartIndex = 1,
        )
        assertEquals(IncrementalQueueSyncPlan(trimCount = 0, commonCount = 3), plan)
    }

    @Test
    fun tailShrinkRemovesTrailingItems() {
        // 新窗口尾部收缩（当前项仍在公共段内）→ 增量移除尾部多余项
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3, 4, 5),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2, 3),
            planStartIndex = 1,
        )
        assertEquals(IncrementalQueueSyncPlan(trimCount = 0, commonCount = 3), plan)
    }

    @Test
    fun bufferingStateAllowsIncremental() {
        // 切歌瞬间常处于 BUFFERING：必须允许增量（这正是回跳高发窗口）
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3),
            controllerCurrentIndex = 2,
            controllerPlaybackState = Player.STATE_BUFFERING,
            planSongIds = listOf(1, 2, 3, 4),
            planStartIndex = 2,
        )
        assertEquals(IncrementalQueueSyncPlan(trimCount = 0, commonCount = 3), plan)
    }

    @Test
    fun idleStateFallsBackToRebuild() {
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_IDLE,
            planSongIds = listOf(1, 2, 3, 4),
            planStartIndex = 1,
        )
        assertNull(plan)
    }

    @Test
    fun endedStateFallsBackToRebuild() {
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_ENDED,
            planSongIds = listOf(1, 2, 3, 4),
            planStartIndex = 1,
        )
        assertNull(plan)
    }

    @Test
    fun emptyControllerQueueFallsBackToRebuild() {
        val plan = planIncrementalQueueSync(
            controllerMediaIds = emptyList(),
            controllerCurrentIndex = 0,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2),
            planStartIndex = 0,
        )
        assertNull(plan)
    }

    @Test
    fun currentSongMismatchFallsBackToRebuild() {
        // 镜像与业务队列错位（当前歌不在 plan 的 startIndex 上）→ 全量兜底
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 3),
            planStartIndex = 1,
        )
        assertNull(plan)
    }

    @Test
    fun middleRemovalFallsBackToRebuild() {
        // 队列中部删除导致错位：无任何 trim 能对齐 → 全量兜底
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3, 4, 5),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2, 4, 5),
            planStartIndex = 1,
        )
        assertNull(plan)
    }

    @Test
    fun reorderedQueueFallsBackToRebuild() {
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(1, 2, 3),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(3, 2, 1),
            planStartIndex = 1,
        )
        assertNull(plan)
    }

    @Test
    fun unresolvableTrimBeyondCurrentIndexFallsBackToRebuild() {
        // 唯一对齐解需要 trim 超过当前项索引（会把当前项裁掉）→ 全量兜底
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(9, 1, 2),
            controllerCurrentIndex = 0,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2, 9),
            planStartIndex = 2,
        )
        assertNull(plan)
    }

    @Test
    fun nonNumericMediaIdFallsBackToRebuild() {
        // mediaId 非数字（非本应用写入的媒体项）→ 无法对齐，全量兜底
        val plan = planIncrementalQueueSync(
            controllerMediaIds = listOf(null, 2),
            controllerCurrentIndex = 1,
            controllerPlaybackState = Player.STATE_READY,
            planSongIds = listOf(1, 2),
            planStartIndex = 1,
        )
        assertNull(plan)
    }
}
