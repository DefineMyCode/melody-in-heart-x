package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.queue.ControllerWindowSynchronizer
import cn.com.dcsgo.mihx.domain.queue.WindowSlide
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
 * bounded (<= [cn.com.dcsgo.mihx.domain.queue.PlaybackWindowPlanner.MAX_WINDOW_SIZE]) slice of a
 * large queue is ever handed to [PlaybackController.setMediaItems] — keeping 1000-song queue
 * switches under the 300ms budget (plan R1 / P2-4..P2-7).
 *
 * Playback drift (plan P5-C3): as the transport advances on its own, [slideWindow] re-plans the
 * window around the live current index and applies the cheapest edit ([WindowSlide.Incremental])
 * so the queue never stops at the window edge and the song being listened to keeps playing.
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
     * Business index the transport is expected to settle on after the last applied
     * [PlaybackController.setMediaItems]/[seekToQueueIndex]/[slideWindow]. The facade reads it to
     * ignore the transient transport-index resets that `setMediaItems`/`seekToDefaultPosition`
     * produce mid-mutation: only advances at-or-past this index are real playback progress.
     */
    @Volatile
    var expectedBusinessIndex: Int = 0

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
        expectedBusinessIndex = plan.windowStartIndex + plan.currentIndex
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
            expectedBusinessIndex = index
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
        expectedBusinessIndex = plan.windowStartIndex + plan.currentIndex
        controller.play()
    }

    /**
     * P5-C3: re-plans the window for natural playback drift and applies the cheapest transport
     * edit. [playQueue] must carry the *live* current index (advanced by the facade). An
     * overlapping window shift becomes a silent [WindowSlide.Incremental] add/remove edit — never a
     * rebuild — so the song the user is listening to keeps playing uninterrupted.
     */
    fun slideWindow(playQueue: PlayQueue) {
        val resolution = synchronizer.resolveDrift(playQueue)
        expectedBusinessIndex = resolution.window.startIndex + resolution.currentIndexInWindow
        when (val slide = resolution.slide) {
            is WindowSlide.None -> Unit
            is WindowSlide.Incremental -> {
                // Additions first so the transport queue is never momentarily empty.
                if (slide.prepend.isNotEmpty()) {
                    controller.addMediaItems(0, slide.prepend.map { mapper.toMediaItem(it) })
                }
                if (slide.append.isNotEmpty()) {
                    controller.addMediaItems(slide.append.map { mapper.toMediaItem(it) })
                }
                if (slide.dropFromHead > 0) {
                    controller.removeMediaItems(0, slide.dropFromHead)
                }
                if (slide.dropFromTail > 0) {
                    val count = controller.mediaItemCount()
                    controller.removeMediaItems(count - slide.dropFromTail, count)
                }
                windowStart.value = resolution.window.startIndex
            }
            is WindowSlide.Rebuild -> {
                val plan = resolution.plan ?: return
                controller.setMediaItems(plan.mediaItems.map { mapper.toMediaItem(it.song) })
                controller.seekToMediaItem(plan.currentIndex)
                windowStart.value = plan.windowStartIndex
            }
        }
    }
}
