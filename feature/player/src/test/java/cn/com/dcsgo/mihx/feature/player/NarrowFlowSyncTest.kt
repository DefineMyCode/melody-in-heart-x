package cn.com.dcsgo.mihx.feature.player

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2026-09-03 真机回归：播放中进度条每 ~5s 回跳抽搐、定时关闭倒计时闪 00:00。
 *
 * 根因：窄流同步的对比基线错误地用了「窄流当前值」而非「update 前的 uiState 旧值」。
 * 播放中 ticker 每 500ms 直写窄流，而 uiState.currentPositionMs /
 * sleepTimerRemainingMs 只在离散事件时更新（autosaver 每 5s 触发的 controller
 * snapshot 同步刻意 copy 保留旧位置）。错误基线下每次离散更新都会把陈旧位置 /
 * 归零倒计时刷回窄流。
 *
 * 以下测试按真实节拍复现该场景并锁定修复后的行为。
 */
class NarrowFlowSyncTest {

    private val uiState = MutableStateFlow(PlayerUiState())
    private val positionMs = MutableStateFlow(0L)
    private val sleepTimerRemainingMs = MutableStateFlow(0L)
    private val sync = NarrowFlowSync(uiState, positionMs, sleepTimerRemainingMs)

    @Test
    fun `discrete update preserving uiState position does not clobber ticker-fresh narrow flow`() {
        // 1. 播放中：ticker 直写窄流到 30s（uiState currentPositionMs 仍是 0）
        positionMs.value = 30_000L

        // 2. autosaver 每 5s 的 controller snapshot 同步：applyControllerPlaybackState
        //    刻意保留 uiState 旧位置（0）——此更新不得把 0 刷回窄流
        sync.update { it.copy(isPlaying = true) }

        assertEquals(30_000L, positionMs.value)
    }

    @Test
    fun `discrete update preserving sleep timer does not reset countdown narrow flow`() {
        // 1. 倒计时激活：tick 只写窄流（M-6），uiState.sleepTimerRemainingMs 仍为 0
        sleepTimerRemainingMs.value = 123_000L

        // 2. 任何离散 uiState 更新（如 isPlaying 翻转）不得把 0 刷回窄流
        sync.update { it.copy(isPlaying = false) }

        assertEquals(123_000L, sleepTimerRemainingMs.value)
    }

    @Test
    fun `true discrete position change still propagates to narrow flow`() {
        uiState.value = uiState.value.copy(currentPositionMs = 10_000L)
        positionMs.value = 10_000L

        // seek：离散事件真实写入新位置，必须同步到窄流
        sync.update { it.copy(currentPositionMs = 60_000L) }

        assertEquals(60_000L, positionMs.value)
        assertEquals(60_000L, uiState.value.currentPositionMs)
    }

    @Test
    fun `restore path position propagates to narrow flow`() {
        // 启动恢复：applyRestoreResult 写入快照恢复点位置，窄流必须跟上
        sync.update { it.copy(currentPositionMs = 42_000L) }

        assertEquals(42_000L, positionMs.value)
    }

    @Test
    fun `sleep timer discrete start propagates to narrow flow`() {
        // 定时关闭启动：离散事件写入 uiState（M-6 设计），窄流同步接收初值
        sync.update {
            it.copy(
                isSleepTimerActive = true,
                sleepTimerEndAtMs = System.currentTimeMillis() + 300_000L,
                sleepTimerRemainingMs = 300_000L,
            )
        }

        assertEquals(300_000L, sleepTimerRemainingMs.value)
    }

    @Test
    fun `sleep timer cancel resets narrow flow to zero`() {
        sleepTimerRemainingMs.value = 200_000L

        // cancel 路径：uiState.sleepTimerRemainingMs 本就为 0（M-6 后 tick 不写 uiState），
        // 值对比感知不到——必须走显式复位入口
        sync.resetSleepTimerNarrowFlow()
        sync.update {
            it.copy(
                isSleepTimerActive = false,
                sleepTimerEndAtMs = 0L,
                sleepTimerRemainingMs = 0L,
            )
        }

        assertEquals(0L, sleepTimerRemainingMs.value)
    }

    @Test
    fun `reset is idempotent and harmless when timer already zero`() {
        sync.resetSleepTimerNarrowFlow()

        assertEquals(0L, sleepTimerRemainingMs.value)
    }
}
