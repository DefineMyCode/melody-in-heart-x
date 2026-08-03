package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song

data class ControllerPlaybackSnapshot(
    val mediaId: String?,
    val isPlaying: Boolean,
    val isBuffering: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long?,
)

data class ControllerPlaybackState(
    val songs: List<Song>,
    val playQueue: PlayQueue,
    val currentSong: Song?,
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val sameNameSongs: List<Song>,
)

data class ControllerPlaybackSyncResult(
    val state: ControllerPlaybackState,
    val trackedSongId: Int?,
    val durationUpdate: DurationUpdate?,
    val playbackStart: PlaybackStart?,
) {
    data class DurationUpdate(val songId: Int, val durationMs: Long)
    data class PlaybackStart(val songId: Int, val durationMs: Long)
}

data class ControllerIsPlayingTransition(
    val shouldPauseTracking: Boolean,
    val shouldResumeTracking: Boolean,
)

class ControllerPlaybackStateSynchronizer(
    private val sameNameSongs: (Song, List<Song>) -> List<Song> = { song, songs ->
        songs
            .filter { it.groupKey == song.groupKey && it.uri != null }
            .sortedByDescending { it.sampleRate }
    },
) {
    fun sync(
        current: ControllerPlaybackState,
        snapshot: ControllerPlaybackSnapshot,
        trackedSongId: Int?,
    ): ControllerPlaybackSyncResult {
        val controllerSong = resolveControllerSong(snapshot.mediaId, current)
        val controllerDuration = snapshot.durationMs?.coerceAtLeast(0L)
        val songChanged = controllerSong != null && controllerSong.id != current.currentSong?.id
        val syncedQueue = syncQueueCurrentIndex(current.playQueue, controllerSong)

        val playbackStart = if (
            snapshot.isPlaying &&
            controllerSong != null &&
            trackedSongId != controllerSong.id
        ) {
            ControllerPlaybackSyncResult.PlaybackStart(
                songId = controllerSong.id,
                durationMs = controllerDuration ?: 0L,
            )
        } else {
            null
        }

        return ControllerPlaybackSyncResult(
            state = current.copy(
                playQueue = syncedQueue,
                currentSong = controllerSong ?: current.currentSong,
                isPlaying = snapshot.isPlaying,
                currentPositionMs = snapshot.currentPositionMs.coerceAtLeast(0L),
                durationMs = controllerDuration ?: current.durationMs,
                sameNameSongs = if (songChanged) {
                    sameNameSongs(controllerSong, current.songs)
                } else {
                    current.sameNameSongs
                },
            ),
            trackedSongId = playbackStart?.songId ?: trackedSongId,
            durationUpdate = controllerSong?.let { song ->
                controllerDuration?.let { duration ->
                    ControllerPlaybackSyncResult.DurationUpdate(song.id, duration)
                }
            },
            playbackStart = playbackStart,
        )
    }

    fun isPlayingTransition(
        previousIsPlaying: Boolean,
        newIsPlaying: Boolean,
        isBuffering: Boolean,
        hasCurrentSong: Boolean,
    ): ControllerIsPlayingTransition {
        return ControllerIsPlayingTransition(
            shouldPauseTracking = previousIsPlaying && !newIsPlaying && !isBuffering,
            shouldResumeTracking = !previousIsPlaying && newIsPlaying && hasCurrentSong,
        )
    }

    private fun resolveControllerSong(
        mediaId: String?,
        current: ControllerPlaybackState,
    ): Song? {
        val songId = mediaId?.toIntOrNull() ?: return null
        return current.songs.firstOrNull { it.id == songId }
            ?: current.playQueue.songs.firstOrNull { it.id == songId }
    }

    private fun syncQueueCurrentIndex(queue: PlayQueue, controllerSong: Song?): PlayQueue {
        if (controllerSong == null) return queue
        if (queue.currentSong?.id == controllerSong.id) return queue

        val queueIndex = queue.songs.indexOfFirst { it.id == controllerSong.id }
        return if (queueIndex >= 0 && queueIndex != queue.currentIndex) {
            queue.copy(currentIndex = queueIndex)
        } else {
            queue
        }
    }
}
