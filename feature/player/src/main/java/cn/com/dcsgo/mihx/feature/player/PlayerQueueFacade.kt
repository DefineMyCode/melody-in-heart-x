package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackQueueActionPlanner

class PlayerQueueFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val playFromQueue: (PlayQueue, Int) -> Unit,
    private val syncPlayerQueue: (PlayQueue) -> Unit,
    private val clearControllerPlaylist: () -> Boolean,
    private val clearPlaybackState: () -> Unit,
    private val savePlaybackState: () -> Unit,
    private val log: (String) -> Unit,
    private val planner: PlaybackQueueActionPlanner = PlaybackQueueActionPlanner(),
) {
    fun setPlayQueue(songs: List<Song>, startIndex: Int = 0, mode: PlayMode? = null) {
        val plan = planner.replaceQueue(
            currentQueue = state().playQueue,
            songs = songs,
            startIndex = startIndex,
            mode = mode,
        ) ?: return
        applyPlan(plan)
    }

    fun addToPlayQueue(song: Song): Boolean {
        val plan = planner.addSong(state().playQueue, song) ?: return false
        applyPlan(plan)
        return true
    }

    fun addSongToNextPlay(song: Song) {
        val current = state()
        val plan = planner.addSongAsNext(
            queue = current.playQueue,
            song = song,
            playModeBeforeNext = current.playModeBeforeNext,
            isInfinitePlay = current.isInfinitePlay,
        )
        applyPlan(plan)
    }

    fun addSongsToNextPlay(songs: List<Song>): Int {
        val current = state()
        val plan = planner.addSongsAsNext(
            queue = current.playQueue,
            songs = songs,
            playModeBeforeNext = current.playModeBeforeNext,
            isInfinitePlay = current.isInfinitePlay,
        ) ?: return 0
        applyPlan(plan)
        return plan.addedCount
    }

    fun addToPlayQueue(songs: List<Song>): Int {
        val plan = planner.addSongs(state().playQueue, songs) ?: return 0
        applyPlan(plan)
        return plan.addedCount
    }

    fun removeFromPlayQueue(songId: Int) {
        applyPlan(planner.removeSong(state().playQueue, songId))
    }

    fun removeFromPlayQueueAt(index: Int) {
        applyPlan(planner.removeSongAt(state().playQueue, index))
    }

    fun clearPlayQueue() {
        applyPlan(planner.clearQueue())
    }

    fun togglePlayMode() {
        val currentMode = state().playQueue.playMode
        val plan = planner.togglePlayMode(state().playQueue)
        applyPlan(plan)
        log("Play mode changed: ${currentMode.label} -> ${plan.queue.playMode.label}")
    }

    fun setPlayMode(mode: PlayMode) {
        applyPlan(planner.setPlayMode(state().playQueue, mode))
    }

    fun playQueueItem(index: Int): Boolean {
        val plan = planner.playQueueItem(state().playQueue, index) ?: return false
        applyPlan(plan)
        return true
    }

    private fun applyPlan(plan: PlaybackQueueActionPlanner.Plan) {
        updateState { current ->
            var updated = current.copy(playQueue = plan.queue)
            if (plan.exitInfinitePlay) {
                updated = updated.copy(
                    isInfinitePlay = false,
                    infinitePlayedSongIds = emptySet(),
                )
            }
            plan.nextPlayState?.let { next ->
                updated = updated.copy(
                    nextPlaySongId = next.songId,
                    playModeBeforeNext = next.playModeBeforeNext,
                    skipNextRefill = next.skipNextRefill,
                )
            }
            if (plan.clearCurrentSong) {
                updated = updated.copy(
                    currentSong = null,
                    isPlaying = false,
                    currentPositionMs = 0L,
                )
            }
            if (plan.clearDuration) {
                updated = updated.copy(durationMs = 0L)
            }
            updated
        }

        when (val action = plan.playbackAction) {
            is PlaybackQueueActionPlanner.PlaybackAction.PlayQueueIndex -> {
                playFromQueue(plan.queue, action.index)
            }
            PlaybackQueueActionPlanner.PlaybackAction.SyncQueue -> syncPlayerQueue(plan.queue)
            PlaybackQueueActionPlanner.PlaybackAction.ClearController -> clearControllerPlaylist()
            PlaybackQueueActionPlanner.PlaybackAction.None -> Unit
        }

        if (plan.clearPlaybackState) {
            clearPlaybackState()
        }
        if (plan.savePlaybackState) {
            savePlaybackState()
        }
    }
}
