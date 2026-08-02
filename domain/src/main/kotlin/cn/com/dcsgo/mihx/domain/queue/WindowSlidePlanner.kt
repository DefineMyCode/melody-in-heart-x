package cn.com.dcsgo.mihx.domain.queue

import cn.com.dcsgo.mihx.core.model.Song
import javax.inject.Inject

/**
 * How the live transport queue must change to move from one playback window to the next.
 *
 * Rebuilding the transport queue with `setMediaItems` restarts buffering and audibly interrupts
 * playback, so it is reserved for genuine jumps. Ordinary playback drift only shifts the window by
 * a few dozen positions, which can be applied with `addMediaItems` / `removeMediaItems` — Media3
 * keeps playing the current item untouched and auto-adjusts its index, so the slide is silent.
 */
sealed interface WindowSlide {
    /** The live window already covers the target range — the transport must not be touched. */
    data object None : WindowSlide

    /**
     * Windows overlap, so the transport queue can be edited in place.
     *
     * Exactly one direction is ever populated: sliding forward yields [append] + [dropFromHead],
     * sliding backward yields [prepend] + [dropFromTail]. Callers must apply the additions *before*
     * the removals so the transport queue is never momentarily empty.
     */
    data class Incremental(
        val prepend: List<Song>,
        val append: List<Song>,
        val dropFromHead: Int,
        val dropFromTail: Int,
    ) : WindowSlide

    /** Ranges are disjoint (a jump): the transport queue has to be rebuilt from scratch. */
    data object Rebuild : WindowSlide
}

/**
 * Diffs two window ranges over the same full play order and reports the minimal transport edit
 * (plan P5-C3 prerequisite).
 *
 * Ranges address the *unfiltered* full order while the transport only ever holds playable items,
 * so every count/slice below re-applies [isPlayable]. That is safe because the filter is per-item
 * and order-preserving: the playable items of a sub-range are exactly the corresponding slice of
 * the playable items of the whole range.
 *
 * Pure & side-effect free — fully unit-testable.
 */
class WindowSlidePlanner @Inject constructor() {

    fun plan(
        fullOrder: List<Song>,
        oldStart: Int,
        oldEndExclusive: Int,
        newStart: Int,
        newEndExclusive: Int,
        isPlayable: (Song) -> Boolean = { it.uri != null },
    ): WindowSlide {
        val size = fullOrder.size
        val oldFrom = oldStart.coerceIn(0, size)
        val oldTo = oldEndExclusive.coerceIn(oldFrom, size)
        val newFrom = newStart.coerceIn(0, size)
        val newTo = newEndExclusive.coerceIn(newFrom, size)

        if (oldFrom == newFrom && oldTo == newTo) return WindowSlide.None
        // An empty old range carries no reusable items; treat it as a cold build.
        if (oldFrom == oldTo || newFrom == newTo) return WindowSlide.Rebuild
        // Disjoint ranges share nothing to reuse — a full rebuild is unavoidable.
        if (newFrom >= oldTo || newTo <= oldFrom) return WindowSlide.Rebuild

        val prepend = if (newFrom < oldFrom) {
            fullOrder.subList(newFrom, minOf(oldFrom, newTo)).filter(isPlayable)
        } else {
            emptyList()
        }
        val append = if (newTo > oldTo) {
            fullOrder.subList(maxOf(oldTo, newFrom), newTo).filter(isPlayable)
        } else {
            emptyList()
        }
        val dropFromHead = if (newFrom > oldFrom) {
            fullOrder.subList(oldFrom, minOf(newFrom, oldTo)).count(isPlayable)
        } else {
            0
        }
        val dropFromTail = if (newTo < oldTo) {
            fullOrder.subList(maxOf(newTo, oldFrom), oldTo).count(isPlayable)
        } else {
            0
        }

        val noop = prepend.isEmpty() && append.isEmpty() && dropFromHead == 0 && dropFromTail == 0
        if (noop) return WindowSlide.None

        return WindowSlide.Incremental(
            prepend = prepend,
            append = append,
            dropFromHead = dropFromHead,
            dropFromTail = dropFromTail,
        )
    }
}
