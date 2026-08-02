package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.queue.QueueOperator
import cn.com.dcsgo.mihx.player.PlayerQueueController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue-operation contract (plan P2-8). Holds the authoritative [PlayQueue] as a [StateFlow] and
 * applies structural changes by delegating to the pure [QueueOperator] (in :domain) and then
 * pushing the new window to the transport via [PlayerQueueController] (in :player).
 *
 * The feature layer only ever sees domain types ([PlayQueue], [Song]); it never references Media3
 * or :data, satisfying the architecture gates (A2/A3) and the "Media3 stays in the kernel" rule.
 */
interface PlayerQueueFacade {
    val queue: StateFlow<PlayQueue>

    /** Authoritative current queue index (window-start + transport index) for highlight. */
    val currentQueueIndex: StateFlow<Int>

    fun setQueue(songs: List<Song>, mode: PlayMode = PlayMode.SEQUENTIAL, currentIndex: Int = 0)

    /** Replaces the queue with a pre-built [PlayQueue] (e.g. restored from a snapshot), preserving
     * its play order / repeats / mode / current index (plan P4-7). */
    fun setQueue(playQueue: PlayQueue)

    fun addSongAsNext(songs: List<Song>)
    fun addSongsToTail(songs: List<Song>, allowDuplicates: Boolean)
    fun switchPlayMode(mode: PlayMode)
    fun removeAt(queueIndex: Int)
    fun jumpTo(index: Int)

    /** Starts the Media3 session so playback can begin (idempotent). */
    fun connect()

    /** Begins playback (buffered across a cold connect by the transport). */
    fun play()
}

@Singleton
class PlayerQueueFacadeImpl @Inject constructor(
    private val operator: QueueOperator,
    private val queueController: PlayerQueueController,
) : PlayerQueueFacade {

    private val _queue = MutableStateFlow(PlayQueue(emptyList(), 0, PlayMode.SEQUENTIAL, emptyList()))
    override val queue: StateFlow<PlayQueue> = _queue.asStateFlow()
    override val currentQueueIndex: StateFlow<Int> = queueController.currentQueueIndex

    override fun setQueue(songs: List<Song>, mode: PlayMode, currentIndex: Int) {
        val initial = PlayQueue(
            songs = songs,
            currentIndex = currentIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
            playMode = mode,
            playOrderIds = songs.map { it.id },
        )
        _queue.value = initial
        queueController.applyQueue(initial)
    }

    override fun setQueue(playQueue: PlayQueue) {
        _queue.value = playQueue
        queueController.applyQueue(playQueue)
    }

    override fun addSongAsNext(songs: List<Song>) {
        val updated = operator.addSongAsNext(_queue.value, songs, _queue.value.currentIndex)
        _queue.value = updated
        queueController.applyQueue(updated)
    }

    override fun addSongsToTail(songs: List<Song>, allowDuplicates: Boolean) {
        val updated = operator.addSongsToTail(_queue.value, songs, allowDuplicates)
        _queue.value = updated
        queueController.applyQueue(updated)
    }

    override fun switchPlayMode(mode: PlayMode) {
        val updated = operator.switchPlayMode(_queue.value, mode)
        _queue.value = updated
        queueController.applyQueue(updated)
    }

    override fun removeAt(queueIndex: Int) {
        val updated = operator.removeAt(_queue.value, queueIndex)
        _queue.value = updated
        queueController.applyQueue(updated)
    }

    override fun jumpTo(index: Int) {
        val updated = operator.jumpTo(_queue.value, index)
        _queue.value = updated
        queueController.seekToQueueIndex(updated, index)
    }

    override fun connect() = queueController.connect()

    override fun play() = queueController.play()
}
