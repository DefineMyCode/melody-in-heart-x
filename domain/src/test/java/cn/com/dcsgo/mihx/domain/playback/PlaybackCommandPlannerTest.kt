package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCommandPlannerTest {

    private val planner = PlaybackCommandPlanner(isPlayable = { it.sampleRate > 0 })

    @Test
    fun songAlreadyInQueuePlaysQueueIndex() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0)

        val command = planner.planSong(song(2), queue)

        assertEquals(PlaybackCommandPlanner.SongCommand.PlayQueueIndex(1), command)
    }

    @Test
    fun playableSongOutsideQueuePlaysSingle() {
        val song = song(2)

        val command = planner.planSong(song, PlayQueue())

        assertSame(song, (command as PlaybackCommandPlanner.SongCommand.PlaySingle).song)
    }

    @Test
    fun unplayableSongOutsideQueueIsIgnored() {
        val command = planner.planSong(song(1, playable = false), PlayQueue())

        assertEquals(PlaybackCommandPlanner.SongCommand.Ignore, command)
    }

    @Test
    fun contextSongFiltersUnplayableSongsAndReturnsStartIndex() {
        val command = planner.planContextSong(
            song = song(3),
            contextSongs = listOf(song(1, playable = false), song(2), song(3), song(4)),
        )

        val replace = command as PlaybackCommandPlanner.ContextCommand.ReplaceQueue
        assertEquals(listOf(2, 3, 4), replace.songs.map { it.id })
        assertEquals(1, replace.startIndex)
    }

    @Test
    fun togglePausesWhenCurrentlyPlaying() {
        val command = planner.planTogglePlayPause(
            isPlaying = true,
            hasCurrentSong = true,
            hasCurrentMediaItem = true,
            queue = PlayQueue(),
        )

        assertEquals(PlaybackCommandPlanner.ToggleCommand.Pause, command)
    }

    @Test
    fun toggleStartsCurrentQueueWhenNoCurrentMediaItemExists() {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 1)

        val command = planner.planTogglePlayPause(
            isPlaying = false,
            hasCurrentSong = false,
            hasCurrentMediaItem = false,
            queue = queue,
        )

        assertEquals(PlaybackCommandPlanner.ToggleCommand.PlayQueueIndex(1), command)
    }

    @Test
    fun nextConsumesSkipRefillBeforeCheckingInfiniteRefill() {
        val command = planner.planNext(
            isInfinitePlay = true,
            skipNextRefill = true,
            remainingSongs = 0,
        )

        assertEquals(PlaybackCommandPlanner.NextCommand.ConsumeSkipRefill, command)
    }

    @Test
    fun nextRefillsInfiniteQueueNearTail() {
        val command = planner.planNext(
            isInfinitePlay = true,
            skipNextRefill = false,
            remainingSongs = 5,
        )

        assertTrue(command is PlaybackCommandPlanner.NextCommand.RefillInfiniteQueue)
        assertEquals(5, (command as PlaybackCommandPlanner.NextCommand.RefillInfiniteQueue).remainingSongs)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(id: Int, playable: Boolean = true): Song {
        return Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = if (playable) 44_100 else 0,
        )
    }
}
