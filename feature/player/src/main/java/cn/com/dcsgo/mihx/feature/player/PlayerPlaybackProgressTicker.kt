package cn.com.dcsgo.mihx.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayerPlaybackProgressTicker(
    private val scope: CoroutineScope,
    private val isPlaying: () -> Boolean,
    private val currentPositionMs: () -> Long,
    private val updatePosition: (Long) -> Unit,
    // 2026-09-03:500ms 使歌词高亮判断平均滞后 250ms(最大 500ms),唱词"慢半拍"。
    // currentPositionMs 读的是 Media3 本地外推值(无 IPC),200ms 粒度下窄流
    // 仅被歌词页/进度条局部订阅,5fps 局部重组成本可忽略。平均滞后降至 ~100ms。
    private val intervalMs: Long = 200L,
) {
    private var job: Job? = null

    fun updateRunningState() {
        if (isPlaying()) {
            start()
        } else {
            stop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isPlaying()) {
                updatePosition(currentPositionMs())
                delay(intervalMs)
            }
            job = null
        }
    }
}
