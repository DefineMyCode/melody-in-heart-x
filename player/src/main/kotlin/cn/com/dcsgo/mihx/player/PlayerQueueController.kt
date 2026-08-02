package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.queue.ControllerWindowSynchronizer
import cn.com.dcsgo.mihx.player.mapper.SongMediaItemMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue between the business [PlayQueue] and the Media3 transport. Uses windowing so that only a
 * bounded (<= [PlaybackWindowPlanner.MAX_WINDOW_SIZE]) slice of a large queue is ever handed to
 * [PlaybackController.setMediaItems] — keeping 1000-song queue switches under the 300ms budget
 * (plan R1 / P2-4..P2-7).
 *
 * Media3 types stay inside the kernel: [PlayQueue] -> [Song] -> [MediaItem] mapping happens here
 * via [SongMediaItemMapper]; feature code only ever holds [PlayQueue].
 */
@Singleton
class PlayerQueueController @Inject constructor(
    private val controller: PlaybackController,
    private val mapper: SongMediaItemMapper,
    private val synchronizer: ControllerWindowSynchronizer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowStart = MutableStateFlow(0)

    /**
     * Authoritative current queue index for UI highlight. Derived as window-start + the transport's
     * current media-item index, so repeated [cn.com.dcsgo.mihx.core.model.Song.id]s map to the
     * *played* position (not the first occurrence) — guarding plan R5.
     */
    val currentQueueIndex: StateFlow<Int> =
        combine(windowStart, controller.currentIndex) { start, pos -> start + pos }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, 0)

    /**
     * Applies a (possibly structural) queue change. A structural change always re-plans the
     * window; the synchronizer then decides whether the transport must be re-built or can reuse the
     * existing window (used by [seekToQueueIndex] for in-window jumps).
     */
    fun applyQueue(playQueue: PlayQueue) {
        synchronizer.forceReplan()
        val resolution = synchronizer.resolve(playQueue)
        val plan = resolution.plan ?: return
        controller.setMediaItems(plan.mediaItems.map { mapper.toMediaItem(it.song) })
        controller.seekToMediaItem(plan.currentIndex)
        windowStart.value = plan.windowStartIndex
    }

    /** Starts the Media3 session (idempotent) so playback can begin even before the player screen. */
    fun connect() = controller.connect()

    /** Begins playback; the play intent is buffered across a cold connect by [PlaybackController]. */
    fun play() = controller.play()

    /**
     * Jumps to business-queue [index] without a full [applyQueue] when the target is already inside
     * the live window (a pure [PlaybackController.seekToMediaItem], no re-buffer). Otherwise it
     * re-centers the window on [index] and rebuilds the transport queue. Called by the queue panel
     * "tap to play".
     */
    fun seekToQueueIndex(playQueue: PlayQueue, index: Int) {
        val cache = synchronizer.current()
        val size = cache?.mediaIds?.size ?: 0
        if (cache != null && index >= cache.startIndex && index < cache.startIndex + size) {
            // Target is already inside the live window: a pure media-item switch, no re-buffer.
            controller.seekToMediaItem(index - cache.startIndex)
            controller.play()
            return
        }
        val clamped = index.coerceIn(0, (playQueue.songs.size - 1).coerceAtLeast(0))
        val recentered = playQueue.copy(currentIndex = clamped)
        synchronizer.forceReplan()
        val resolution = synchronizer.resolve(recentered)
        val plan = resolution.plan ?: return
        controller.setMediaItems(plan.mediaItems.map { mapper.toMediaItem(it.song) })
        controller.seekToMediaItem(plan.currentIndex)
        windowStart.value = plan.windowStartIndex
        controller.play()
    }
}
