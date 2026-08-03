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
    private val intervalMs: Long = 500L,
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
