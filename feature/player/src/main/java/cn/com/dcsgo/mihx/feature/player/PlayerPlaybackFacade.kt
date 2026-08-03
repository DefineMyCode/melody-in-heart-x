package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.PlaybackCommandPlanner

class PlayerPlaybackFacade(
    private val state: () -> PlayerUiState,
    private val updateState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    private val hasCurrentMediaItem: () -> Boolean,
    private val setPlayQueue: (List<Song>, Int) -> Unit,
    private val playQueueItem: (Int) -> Boolean,
    private val playFromQueue: (PlayQueue, Int) -> Unit,
    private val playSingle: (Song) -> Boolean,
    private val pausePlayback: () -> Unit,
    private val resumePlayback: () -> Unit,
    private val playPreviousInController: () -> Unit,
    private val playNextInController: () -> Unit,
    private val remainingMediaItems: () -> Int,
    private val refillInfinitePlayQueue: () -> Unit,
    private val log: (String) -> Unit,
    private val planner: PlaybackCommandPlanner = PlaybackCommandPlanner(),
) {
    fun playSong(song: Song) {
        when (val command = planner.planSong(song, state().playQueue)) {
            is PlaybackCommandPlanner.SongCommand.PlayQueueIndex -> playQueueItem(command.index)
            is PlaybackCommandPlanner.SongCommand.PlaySingle -> playSingle(command.song)
            PlaybackCommandPlanner.SongCommand.Ignore -> Unit
        }
    }

    fun playSongFromContext(song: Song, contextSongs: List<Song>) {
        when (val command = planner.planContextSong(song, contextSongs)) {
            is PlaybackCommandPlanner.ContextCommand.ReplaceQueue -> {
                setPlayQueue(command.songs, command.startIndex)
            }
            PlaybackCommandPlanner.ContextCommand.Ignore -> Unit
        }
    }

    fun togglePlayPause() {
        val current = state()
        when (
            val command = planner.planTogglePlayPause(
                isPlaying = current.isPlaying,
                hasCurrentSong = current.currentSong != null,
                hasCurrentMediaItem = hasCurrentMediaItem(),
                queue = current.playQueue,
            )
        ) {
            PlaybackCommandPlanner.ToggleCommand.Pause -> pausePlayback()
            PlaybackCommandPlanner.ToggleCommand.Resume -> resumePlayback()
            is PlaybackCommandPlanner.ToggleCommand.PlayQueueIndex -> {
                playFromQueue(current.playQueue, command.index)
            }
            PlaybackCommandPlanner.ToggleCommand.Ignore -> Unit
        }
    }

    fun playPrevious() {
        if (state().playQueue.isEmpty) return
        playPreviousInController()
    }

    fun playNext() {
        val current = state()
        val remainingSongs = if (current.isInfinitePlay && !current.skipNextRefill) {
            remainingMediaItems()
        } else {
            Int.MAX_VALUE
        }

        when (
            val command = planner.planNext(
                isInfinitePlay = current.isInfinitePlay,
                skipNextRefill = current.skipNextRefill,
                remainingSongs = remainingSongs,
            )
        ) {
            is PlaybackCommandPlanner.NextCommand.RefillInfiniteQueue -> {
                refillInfinitePlayQueue()
                log("infinite play refill: remaining=${command.remainingSongs}")
            }
            PlaybackCommandPlanner.NextCommand.ConsumeSkipRefill -> {
                updateState { it.copy(skipNextRefill = false) }
                log("Infinite play: skipped one queue refill after Add Next")
            }
            PlaybackCommandPlanner.NextCommand.AdvanceOnly -> Unit
        }
        playNextInController()
    }
}
