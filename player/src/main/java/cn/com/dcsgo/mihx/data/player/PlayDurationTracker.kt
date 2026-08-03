package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitor
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "PlayDurationTracker"
private const val UPDATE_INTERVAL_MS = 100L // 100毫秒更新一次
private const val SHORT_PLAY_THRESHOLD_MS = 5000L // 短时长阈值：5秒
private const val SHORT_PLAY_COUNT_THRESHOLD = 2 // 短时长播放次数阈值：累计2次
private const val COMPLETION_RATE_THRESHOLD = 0.9
private const val LONG_PLAY_THRESHOLD_MS = 5 * 60 * 1000L // 长歌兜底阈值：5分钟

/**
 * 播放时长跟踪器
 *
 * 负责：
 * - 在独立线程中实时跟踪音乐播放时长
 * - 处理播放状态变化（开始、暂停、停止）
 * - 累计播放时长并持久化
 * - 新播放会话开始时增加原始播放次数
 * - 当播放时长达到歌曲时长的90%或超过5分钟时增加有效播放次数
 * - 如果歌曲在秒切歌曲列表中，有效播放次数加1后自动移除
 * - 自动检测短时长播放，累计超过2次后自动添加到秒切列表
 */
class PlayDurationTracker(
    private val playStatsRepository: PlayStatsRepository,
    private val quickSkipRepository: QuickSkipRepository
) : PlaybackDurationMonitor {

    // 协程作用域，用于在后台线程中计时
    private val scope = CoroutineScope(Dispatchers.IO)

    // 当前正在播放的歌曲ID
    private var currentSongId: Int? = null

    // 当前歌曲的总时长（毫秒）
    private var currentSongDurationMs: Long = 0L

    // 当前歌曲的累计播放时长（毫秒）
    private val currentPlayDurationMs = AtomicLong(0L)

    // 上次更新时间戳
    private val lastUpdateTimeMs = AtomicLong(0L)

    // 是否正在播放
    private val isPlaying = AtomicBoolean(false)

    // 是否正在拖动进度条
    private val isSeeking = AtomicBoolean(false)

    // 是否正在运行计时任务
    private val isTracking = AtomicBoolean(false)

    /**
     * 开始播放新歌曲
     *
     * @param songId 歌曲ID
     * @param durationMs 歌曲总时长（毫秒）
     */
    override fun startPlayback(songId: Int, durationMs: Long) {
        // 停止当前播放的歌曲计时
        stopPlayback()

        // 初始化新歌曲的计时，从0开始
        currentSongId = songId
        currentSongDurationMs = durationMs
        currentPlayDurationMs.set(0L)
        lastUpdateTimeMs.set(System.currentTimeMillis())
        isPlaying.set(true)

        playStatsRepository.incrementRawPlayCount(songId)

        // 开始计时
        startTracking()

        AppLog.debug(TAG, "开始播放歌曲: id=$songId, 总时长=${durationMs}ms, 开始计时")
    }

    override fun updateDuration(songId: Int, durationMs: Long) {
        if (durationMs <= 0L || currentSongId != songId) return
        currentSongDurationMs = durationMs
        AppLog.debug(TAG, "更新当前歌曲总时长: id=$songId, duration=${durationMs}ms")
    }

    /**
     * 暂停播放
     */
    override fun pausePlayback() {
        isPlaying.set(false)
        AppLog.debug(TAG, "暂停播放，当前累计时长=${currentPlayDurationMs.get()}ms")
    }

    /**
     * 恢复播放
     */
    override fun resumePlayback() {
        lastUpdateTimeMs.set(System.currentTimeMillis())
        isPlaying.set(true)
        AppLog.debug(TAG, "恢复播放")
    }

    /**
     * 停止播放
     */
    override fun stopPlayback() {
        isPlaying.set(false)
        isTracking.set(false)

        // 结算当前歌曲的播放时长
        currentSongId?.let { songId ->
            val totalDuration = currentPlayDurationMs.get()

            // 检查是否达到播放次数计数条件：完播率达标或长歌播放超过5分钟
            if (currentSongDurationMs > 0) {
                val completionThreshold = (currentSongDurationMs * COMPLETION_RATE_THRESHOLD).toLong()
                val isCompletionReached = totalDuration >= completionThreshold
                val isLongPlayReached = totalDuration >= LONG_PLAY_THRESHOLD_MS
                if (isCompletionReached || isLongPlayReached) {
                    playStatsRepository.increment(songId)

                    // 如果歌曲在秒切歌曲列表中，播放次数加1后自动移除
                    if (quickSkipRepository.contains(songId)) {
                        quickSkipRepository.remove(songId)
                        AppLog.debug(TAG, "秒切歌曲播放计数达标，已移除: song=$songId")
                    }

                    // 重置短时长播放计数（因为正常播放完了）
                    quickSkipRepository.resetShortPlayCount(songId)

                    AppLog.debug(
                        TAG,
                        "播放计数 +1: song=$songId, 播放时长=${totalDuration}ms, " +
                            "90%阈值=${completionThreshold}ms, 5分钟阈值=${LONG_PLAY_THRESHOLD_MS}ms"
                    )
                } else {
                    // 如果播放时长小于5秒，增加短时长播放计数
                    if (totalDuration < SHORT_PLAY_THRESHOLD_MS && !quickSkipRepository.contains(songId)) {
                        val shortPlayCount = quickSkipRepository.incrementShortPlayCount(songId)
                        AppLog.debug(TAG, "短时长播放检测: song=$songId, 播放时长=${totalDuration}ms, 短时长次数=$shortPlayCount")

                        // 如果短时长播放次数累计超过2次，自动添加到秒切列表
                        if (shortPlayCount >= SHORT_PLAY_COUNT_THRESHOLD) {
                            quickSkipRepository.add(songId)
                            AppLog.debug(TAG, "短时长播放次数超标，自动添加到秒切列表: song=$songId")
                        }
                    }

                    AppLog.debug(
                        TAG,
                        "播放时长未达到阈值: song=$songId, 播放时长=${totalDuration}ms, " +
                            "90%阈值=${completionThreshold}ms, 5分钟阈值=${LONG_PLAY_THRESHOLD_MS}ms"
                    )
                }
            }
        }

        // 重置状态
        currentSongId = null
        currentSongDurationMs = 0L
        currentPlayDurationMs.set(0L)
        lastUpdateTimeMs.set(0L)
        isSeeking.set(false)

        AppLog.debug(TAG, "停止播放")
    }

    /**
     * 开始计时任务
     */
    private fun startTracking() {
        if (isTracking.compareAndSet(false, true)) {
            scope.launch {
                while (isTracking.get()) {
                    if (isPlaying.get() && !isSeeking.get()) {
                        val currentTime = System.currentTimeMillis()
                        val elapsedTime = currentTime - lastUpdateTimeMs.get()

                        if (elapsedTime > 0) {
                            currentPlayDurationMs.addAndGet(elapsedTime)
                            lastUpdateTimeMs.set(currentTime)
                        }
                    }

                    delay(UPDATE_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * 获取当前歌曲的累计播放时长
     */
    fun getCurrentPlayDuration(): Long {
        return currentPlayDurationMs.get()
    }

    /**
     * 标记开始拖动进度条
     */
    override fun startSeeking() {
        isSeeking.set(true)
        AppLog.debug(TAG, "开始拖动进度条")
    }

    /**
     * 标记结束拖动进度条
     */
    override fun endSeeking() {
        isSeeking.set(false)
        // 重置上次更新时间，避免拖动后的时间被计入
        lastUpdateTimeMs.set(System.currentTimeMillis())
        AppLog.debug(TAG, "结束拖动进度条")
    }

    /**
     * 清理资源
     */
    override fun release() {
        stopPlayback()
    }
}
