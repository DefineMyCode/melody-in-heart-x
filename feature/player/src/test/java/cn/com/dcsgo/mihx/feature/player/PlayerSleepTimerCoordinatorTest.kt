package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 定时关闭协调器的窄流交互回归（2026-09-03）：
 * - M-6 后倒计时 tick 只写窄流，uiState.sleepTimerRemainingMs 恒为初值；
 * - 因此 cancel/fire 对窄流的归零必须走显式复位入口 [PlayerSleepTimerCoordinator]
 *   的 resetRemainingMs，而不是依赖 uiState 值对比传播。
 */
class PlayerSleepTimerCoordinatorTest {

    private var state = PlayerUiState()
    private val remainingWrites = mutableListOf<Long>()
    private var narrowReset = false
    private var paused = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val settings = object : PlayerSettingsRepository {
        var endAtMs = 0L
        var playLastSong = false

        override val themeMode: Flow<cn.com.dcsgo.mihx.core.model.ThemeMode> = emptyFlow()
        override val themeVariant: Flow<cn.com.dcsgo.mihx.core.model.ThemeVariant> = emptyFlow()
        override val globalUniformRandomEnabled: Flow<Boolean> = emptyFlow()
        override val bluetoothPlaybackMonitoringEnabled: Flow<Boolean> = emptyFlow()
        override val playbackNotificationEnabled: Flow<Boolean> = emptyFlow()
        override val lyricFontScale: Flow<Float> = emptyFlow()
        override val dailyListeningGoalMinutes: Flow<Int> = emptyFlow()
        override val emotionScanPaused: Flow<Boolean> = emptyFlow()
        override val moodTimeSlotEnabled: Flow<Boolean> = emptyFlow()
        override fun currentGlobalUniformRandomEnabled(): Boolean = false
        override fun currentBluetoothPlaybackMonitoringEnabled(): Boolean = false
        override fun currentPlaybackNotificationEnabled(): Boolean = false
        override fun currentDailyListeningGoalMinutes(): Int = 0
        override fun setGlobalUniformRandomEnabledBlocking(enabled: Boolean) = Unit
        override fun setBluetoothPlaybackMonitoringEnabledBlocking(enabled: Boolean) = Unit
        override fun setPlaybackNotificationEnabledBlocking(enabled: Boolean) = Unit
        override fun setDailyListeningGoalMinutesBlocking(minutes: Int) = Unit
        override fun currentSleepTimerEndAtMs(): Long = endAtMs
        override fun setSleepTimerEndAtMsBlocking(endAtMs: Long) { this.endAtMs = endAtMs }
        override fun currentSleepTimerPlayLastSong(): Boolean = playLastSong
        override fun setSleepTimerPlayLastSongBlocking(enabled: Boolean) { playLastSong = enabled }
        override fun currentEmotionScanPaused(): Boolean = false
        override suspend fun setEmotionScanPaused(paused: Boolean) = Unit
        override fun currentMoodTimeSlotEnabled(): Boolean = false
        override suspend fun setMoodTimeSlotEnabled(enabled: Boolean) = Unit
        override suspend fun setThemeMode(mode: cn.com.dcsgo.mihx.core.model.ThemeMode) = Unit
        override suspend fun setThemeVariant(variant: cn.com.dcsgo.mihx.core.model.ThemeVariant) = Unit
        override suspend fun setGlobalUniformRandomEnabled(enabled: Boolean) = Unit
        override suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean) = Unit
        override suspend fun setPlaybackNotificationEnabled(enabled: Boolean) = Unit
        override suspend fun setLyricFontScale(scale: Float) = Unit
        override suspend fun setDailyListeningGoalMinutes(minutes: Int) = Unit
    }

    private fun coordinator(playLastSong: Boolean = false) = PlayerSleepTimerCoordinator(
        scope = scope,
        settings = settings.apply { this.playLastSong = playLastSong },
        state = { state },
        updateState = { transform -> state = transform(state) },
        updateRemainingMs = { remainingWrites += it },
        resetRemainingMs = { narrowReset = true },
        pausePlayback = { paused = true },
    )

    @Test
    fun cancelResetsNarrowFlowAndClearsState() {
        val coordinator = coordinator()
        coordinator.start(durationMinutes = 5, playLastSong = false)

        coordinator.cancel()

        assertEquals(0L, state.sleepTimerEndAtMs)
        assertFalse(state.isSleepTimerActive)
        assertEquals(0L, state.sleepTimerRemainingMs)
        // 显式窄流复位被调用（tick 值不在 uiState，值对比感知不到）
        assertEquals(true, narrowReset)
        scope.cancel()
    }

    @Test
    fun startWritesDiscreteStateAndPersistsEndAt() {
        val coordinator = coordinator()

        coordinator.start(durationMinutes = 15, playLastSong = true)

        assertTrue(state.isSleepTimerActive)
        assertTrue(state.sleepTimerEndAtMs > System.currentTimeMillis())
        assertTrue(state.sleepTimerPlayLastSong)
        assertEquals(state.sleepTimerEndAtMs, settings.endAtMs)
        scope.cancel()
    }

    @Test
    fun fireWithPlayLastSongOnlyMarksPendingAndResetsNarrowFlow() {
        val coordinator = coordinator(playLastSong = true)
        // endAt 设为 50ms 后：ticker 首拍(未到点,写窄流)→ delay 1s → 第二拍 fire
        // （Unconfined scope：launch 后立即执行到第一个挂起点 delay,join 等待整个循环退出）
        coordinator.start(durationMinutes = 5, playLastSong = true)
        settings.endAtMs = System.currentTimeMillis() + 50L
        state = state.copy(
            isSleepTimerActive = true,
            sleepTimerEndAtMs = settings.endAtMs,
            isPlaying = true,
            currentSong = song(1),
        )
        narrowReset = false

        runBlocking {
            scope.coroutineContext[Job]?.children?.forEach { it.join() }
        }

        // fire：pending 置位 + 窄流复位；不立即 cancel（等歌曲播完）
        assertTrue(state.sleepTimerPausePending)
        assertEquals(true, narrowReset)
        scope.cancel()
    }

    @Test
    fun onSongEndedAfterFirePausesAndCancels() {
        val coordinator = coordinator(playLastSong = true)
        coordinator.start(durationMinutes = 5, playLastSong = true)
        state = state.copy(isPlaying = true, currentSong = song(1), sleepTimerPausePending = true)
        narrowReset = false

        runBlocking {
            coordinator.onSongEnded()
        }

        // 「播完最后一曲」收尾：暂停 + cancel（cancel 内部再次显式复位窄流）
        assertTrue(paused)
        assertFalse(state.isSleepTimerActive)
        assertEquals(true, narrowReset)
        scope.cancel()
    }

    private fun song(id: Int) = cn.com.dcsgo.mihx.core.model.Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
    )
}
