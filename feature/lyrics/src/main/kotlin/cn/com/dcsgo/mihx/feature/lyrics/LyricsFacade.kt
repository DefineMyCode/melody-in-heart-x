package cn.com.dcsgo.mihx.feature.lyrics

import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.player.PlaybackController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Thin boundary between the lyrics screen and the playback / lyrics sources. The playback kernel
 * [PlaybackController] (a `:player` `@Singleton`) is the single source of truth for the current
 * song id (`currentMediaId`) and playback position; depending on `:player` (not `:feature:player`)
 * keeps this feature independent of other feature modules (gate A2 only forbids `:data`/`:app`).
 */
class LyricsFacade @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController,
) {
    /**
     * Live playback position in milliseconds, polled every [POSITION_POLL_MS] while the lyrics
     * screen collects it. The transport snapshot only updates on player *events* — its position
     * can stall between events, which froze lyric-line following; a fixed poll keeps activeIndex
     * advancing (and also emits while paused, so the current line stays accurate after seek).
     */
    val positionFlow: Flow<Long> = flow {
        while (true) {
            emit(playbackController.currentPosition())
            delay(POSITION_POLL_MS)
        }
    }

    /** Live id of the currently playing song, or null when nothing is loaded. */
    val currentSongIdFlow: Flow<Long?> = playbackController.snapshot.map { it.currentMediaId?.toLongOrNull() }

    /** Seek the player to a lyric line's timestamp. */
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    /** Loads the lyrics for [songId], or null when unavailable. */
    suspend fun loadLyrics(songId: Long): Lyrics? {
        val song = songRepository.getById(songId) ?: return null
        return lyricsRepository.loadForSong(song)
    }

    private companion object {
        const val POSITION_POLL_MS: Long = 500
    }
}
