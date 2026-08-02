package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.queue.InfiniteQueueExtender
import cn.com.dcsgo.mihx.domain.queue.QueueOperator
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.player.PlayerQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val playStatsRepository: PlayStatsRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val songRepository: SongRepository,
    private val infiniteQueueExtender: InfiniteQueueExtender,
) : PlayerQueueFacade {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _queue = MutableStateFlow(PlayQueue(emptyList(), 0, PlayMode.SEQUENTIAL, emptyList()))
    override val queue: StateFlow<PlayQueue> = _queue.asStateFlow()
    override val currentQueueIndex: StateFlow<Int> = queueController.currentQueueIndex

    // P5-C: uniform-random weights are mirrored here so switchPlayMode stays synchronous. The
    // queue itself never touches a repository (plan rule: PlayQueue must not depend on :data).
    @Volatile
    private var playCounts: Map<Long, Long> = emptyMap()

    @Volatile
    private var uniformRandomEnabled: Boolean = true

    // P5-C3: infinite-play flag and library snapshot are mirrored so the top-up decision stays
    // synchronous with the queue mutation (no repository call inside the hot path).
    @Volatile
    private var infinitePlayEnabled: Boolean = false

    @Volatile
    private var librarySongs: List<Song> = emptyList()

    init {
        scope.launch {
            playStatsRepository.observeStats().collect { stats ->
                val counts = HashMap<Long, Long>(stats.size)
                for (entry in stats) counts[entry.songId] = entry.playCount
                playCounts = counts
            }
        }
        scope.launch {
            playerSettingsRepository.observeUniformRandomEnabled().collect { uniformRandomEnabled = it }
        }
        scope.launch {
            playerSettingsRepository.observeInfinitePlayEnabled().collect { infinitePlayEnabled = it }
        }
        scope.launch {
            songRepository.observeAll().collect { librarySongs = it }
        }
        scope.launch {
            // P5-C3: playback drift. The transport advances on its own; commit the live position
            // into the business queue, top the queue up when infinite play is running dry, and let
            // the kernel slide the window so playback never stops at the window edge.
            queueController.currentQueueIndex.collect { actualIndex ->
                // Transient index resets emitted mid setMediaItems/seek are always below the
                // expected settled index; ignoring them keeps the committed position monotonic.
                if (actualIndex < queueController.expectedBusinessIndex) return@collect
                if (actualIndex == _queue.value.currentIndex) return@collect
                val advanced = _queue.value.copy(currentIndex = actualIndex)
                _queue.value = advanced
                if (infinitePlayEnabled &&
                    remainingAfter(advanced) <= InfiniteQueueExtender.DEFAULT_TRIGGER_REMAINING
                ) {
                    topUp(advanced)
                } else {
                    queueController.slideWindow(advanced)
                }
            }
        }
    }

    /** Queue entries still to play after [queue.currentIndex] (plan P5-C3 top-up trigger). */
    private fun remainingAfter(queue: PlayQueue): Int =
        (queue.playOrderIds.size - queue.currentIndex - 1).coerceAtLeast(0)

    /**
     * P5-C3: appends the next infinite-play batch to the queue tail. The live transport window is
     * left untouched so the current song keeps playing — the next drift re-plan resolves against
     * the grown queue and naturally loads the new tail.
     */
    private fun topUp(queue: PlayQueue) {
        val batch = infiniteQueueExtender.nextBatch(queue, librarySongs)
        if (batch.isEmpty()) return
        val updated = operator.addSongsToTail(queue, batch, allowDuplicates = true)
        if (updated == queue) return
        _queue.value = updated
    }

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

    /**
     * Switches play mode. For [PlayMode.RANDOM] the planner is fed real play counts so rarely
     * played songs surface earlier; when the uniform-random setting is off we pass no weights,
     * which makes the planner degrade to a plain shuffle.
     */
    override fun switchPlayMode(mode: PlayMode) {
        val weights = if (mode == PlayMode.RANDOM && uniformRandomEnabled) playCounts else emptyMap()
        val updated = operator.switchPlayMode(_queue.value, mode, weights)
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
