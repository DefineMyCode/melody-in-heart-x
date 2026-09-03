package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PlayerSleepTimer"
private const val TICK_INTERVAL_MS = 1_000L

/**
 * 定时关闭协调器。
 *
 * - 设置倒计时，到点后暂停播放；
 * - 支持「播完最后一曲」：到点后若正在播放，等待当前歌曲自然结束再暂停；
 * - 倒计时期间每秒 tick 只写剩余毫秒窄流（[updateRemainingMs]，见 PlayerRuntime.sleepTimerRemainingMs），
 *   不触碰主 UiState；启动/取消/到期等离散事件才走 [updateState]；
 * - 结束时间与「播完最后一曲」持久化，应用重启后可恢复。
 */
class PlayerSleepTimerCoordinator(
    private val scope: CoroutineScope,
    private val settings: PlayerSettingsRepository,
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val updateRemainingMs: (Long) -> Unit,
    private val resetRemainingMs: () -> Unit,
    private val pausePlayback: () -> Unit,
) {
    private var tickerJob: Job? = null

    /** 应用启动时恢复未过期的定时关闭 */
    fun restore() {
        val endAtMs = settings.currentSleepTimerEndAtMs()
        if (endAtMs <= 0L) return
        if (endAtMs <= System.currentTimeMillis()) {
            settings.setSleepTimerEndAtMsBlocking(0L)
            return
        }
        val playLastSong = settings.currentSleepTimerPlayLastSong()
        updateState {
            it.copy(
                isSleepTimerActive = true,
                sleepTimerEndAtMs = endAtMs,
                sleepTimerPlayLastSong = playLastSong,
                sleepTimerPausePending = false,
            )
        }
        startTicker()
    }

    /** 开始定时关闭 */
    fun start(durationMinutes: Int, playLastSong: Boolean) {
        val endAtMs = System.currentTimeMillis() + durationMinutes.coerceAtLeast(1) * 60_000L
        settings.setSleepTimerEndAtMsBlocking(endAtMs)
        settings.setSleepTimerPlayLastSongBlocking(playLastSong)
        updateState {
            it.copy(
                isSleepTimerActive = true,
                sleepTimerEndAtMs = endAtMs,
                sleepTimerPlayLastSong = playLastSong,
                sleepTimerPausePending = false,
            )
        }
        AppLog.debug(TAG, "定时关闭已设置: ${durationMinutes}分钟, 播完最后一曲=$playLastSong")
        startTicker()
    }

    /** 取消定时关闭 */
    fun cancel() {
        settings.setSleepTimerEndAtMsBlocking(0L)
        tickerJob?.cancel()
        tickerJob = null
        // M-6 后倒计时 tick 只写窄流，uiState 值已不代表窄流——取消必须显式复位窄流，
        // 否则 Chip 上残留的倒计时不会消失（2026-09-03 回归修复）。
        resetRemainingMs()
        updateState {
            it.copy(
                isSleepTimerActive = false,
                sleepTimerEndAtMs = 0L,
                sleepTimerRemainingMs = 0L,
                sleepTimerPlayLastSong = false,
                sleepTimerPausePending = false,
            )
        }
        AppLog.debug(TAG, "定时关闭已取消")
    }

    /** 歌曲自然结束时调用：处理「播完最后一曲」的到点暂停 */
    fun onSongEnded() {
        if (state().sleepTimerPausePending) {
            pausePlayback()
            cancel()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val endAtMs = state().sleepTimerEndAtMs
                if (endAtMs <= 0L) break
                val remainingMs = endAtMs - System.currentTimeMillis()
                if (remainingMs <= 0L) {
                    fire()
                    break
                }
                // M-6（评审 2026-09-03）：每秒 tick 只写窄流（sleepTimerRemainingMs），
                // 不再写主 UiState——否则倒计时激活期间整壳每秒重组。
                updateRemainingMs(remainingMs)
                delay(TICK_INTERVAL_MS)
            }
        }
    }

    private fun fire() {
        val current = state()
        val shouldFinishLastSong = current.sleepTimerPlayLastSong &&
            current.isPlaying &&
            current.currentSong != null
        if (shouldFinishLastSong) {
            // 离散事件：pending 状态进主 UiState（低频）；倒计时窄流显式归零
            // （tick 一直写窄流、uiState 恒 0，值对比感知不到，必须显式复位）
            resetRemainingMs()
            updateState {
                it.copy(sleepTimerRemainingMs = 0L, sleepTimerPausePending = true)
            }
            AppLog.debug(TAG, "定时关闭：到点，等待当前歌曲播完再暂停")
        } else {
            pausePlayback()
            cancel()
            AppLog.debug(TAG, "定时关闭：到点，已暂停播放")
        }
    }
}
