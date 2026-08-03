package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

class PlaybackCommandPlanner(
    private val isPlayable: (Song) -> Boolean = { it.uri != null },
) {
    sealed class SongCommand {
        data class PlayQueueIndex(val index: Int) : SongCommand()
        data class PlaySingle(val song: Song) : SongCommand()
        data object Ignore : SongCommand()
    }

    sealed class ContextCommand {
        data class ReplaceQueue(val songs: List<Song>, val startIndex: Int) : ContextCommand()
        data object Ignore : ContextCommand()
    }

    sealed class ToggleCommand {
        data object Pause : ToggleCommand()
        data object Resume : ToggleCommand()
        data class PlayQueueIndex(val index: Int) : ToggleCommand()
        data object Ignore : ToggleCommand()
    }

    sealed class NextCommand {
        data class RefillInfiniteQueue(val remainingSongs: Int) : NextCommand()
        data object ConsumeSkipRefill : NextCommand()
        data object AdvanceOnly : NextCommand()
    }

    fun planSong(song: Song, queue: PlayQueue): SongCommand {
        val indexInQueue = queue.songs.indexOfFirst { it.id == song.id }
        if (indexInQueue >= 0) {
            return SongCommand.PlayQueueIndex(indexInQueue)
        }
        return if (isPlayable(song)) {
            SongCommand.PlaySingle(song)
        } else {
            SongCommand.Ignore
        }
    }

    fun planContextSong(song: Song, contextSongs: List<Song>): ContextCommand {
        if (contextSongs.isEmpty() || !isPlayable(song)) return ContextCommand.Ignore

        val filteredSongs = contextSongs.filter(isPlayable)
        if (filteredSongs.isEmpty()) return ContextCommand.Ignore

        val startIndex = filteredSongs.indexOfFirst { it.id == song.id }
        return if (startIndex >= 0) {
            ContextCommand.ReplaceQueue(filteredSongs, startIndex)
        } else {
            ContextCommand.Ignore
        }
    }

    fun planTogglePlayPause(
        isPlaying: Boolean,
        hasCurrentSong: Boolean,
        hasCurrentMediaItem: Boolean,
        queue: PlayQueue,
    ): ToggleCommand {
        if (isPlaying) return ToggleCommand.Pause
        if (hasCurrentSong || hasCurrentMediaItem) return ToggleCommand.Resume
        if (!queue.isEmpty) return ToggleCommand.PlayQueueIndex(queue.currentIndex.coerceAtLeast(0))
        return ToggleCommand.Ignore
    }

    fun planNext(
        isInfinitePlay: Boolean,
        skipNextRefill: Boolean,
        remainingSongs: Int,
        refillThreshold: Int = DEFAULT_REFILL_THRESHOLD,
    ): NextCommand {
        if (skipNextRefill) return NextCommand.ConsumeSkipRefill
        if (isInfinitePlay && remainingSongs <= refillThreshold) {
            return NextCommand.RefillInfiniteQueue(remainingSongs)
        }
        return NextCommand.AdvanceOnly
    }

    companion object {
        const val DEFAULT_REFILL_THRESHOLD = 5
    }
}
