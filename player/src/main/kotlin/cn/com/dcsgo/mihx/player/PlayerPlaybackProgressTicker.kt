package cn.com.dcsgo.mihx.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Emits the current playback position every 500ms, but only while [isPlaying] is true (plan P1-7).
 *
 * Foreground/visibility gating is the collector's responsibility: call this from a
 * `repeatOnLifecycle(Lifecycle.State.STARTED)` scope (or [androidx.lifecycle.compose
 * .collectAsStateWithLifecycle]) so ticking stops when the screen is not visible.
 */
class PlayerPlaybackProgressTicker @Inject constructor() {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun progress(isPlaying: Flow<Boolean>, positionProvider: () -> Long): Flow<Long> =
        isPlaying.flatMapLatest { playing ->
            if (playing) {
                flow {
                    while (true) {
                        emit(positionProvider())
                        delay(INTERVAL_MS)
                    }
                }
            } else {
                emptyFlow()
            }
        }

    companion object {
        const val INTERVAL_MS: Long = 500
    }
}
