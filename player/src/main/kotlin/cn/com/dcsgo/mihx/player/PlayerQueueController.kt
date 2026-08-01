package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.domain.queue.ControllerWindowSynchronizer
import cn.com.dcsgo.mihx.player.mapper.SongMediaItemMapper
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

    /**
     * Applies a (possibly structural) queue change. A structural change always re-plans the
     * window; the synchronizer then decides whether the transport must be re-built or can reuse the
     * existing window.
     */
    fun applyQueue(playQueue: PlayQueue) {
        synchronizer.forceReplan()
        val resolution = synchronizer.resolve(playQueue)
        val plan = resolution.plan ?: return
        controller.setMediaItems(plan.mediaItems.map { mapper.toMediaItem(it.song) })
        controller.seekTo(plan.currentIndex.toLong())
    }
}
