package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.SongGroupCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerVersionFacadeTest {

    private var state = PlayerUiState()
    private val groupUpdates = mutableListOf<Pair<Int, String?>>()
    private var repositorySongs = emptyList<Song>()
    private var groupUpdateResult = true
    private var saved = false
    private var playedQueueIndex: Int? = null
    private var playedNext = false
    private val facade = PlayerVersionFacade(
        state = { state },
        updateState = { transform -> state = transform(state) },
        songGroupCoordinator = SongGroupCoordinator(
            updateSongTitleOverride = { songId, titleOverride ->
                groupUpdates += songId to titleOverride
                groupUpdateResult
            },
            getSongs = { repositorySongs },
            isPlayable = { true },
        ),
        savePlaybackState = { saved = true },
        playFromQueue = { _, index -> playedQueueIndex = index },
        playNext = { playedNext = true },
        isPlayable = { it.sampleRate > 0 },
    )

    @Test
    fun updateSameNameSongsSortsBySampleRate() {
        val current = song(1, title = "Song", sampleRate = 44_100)
        state = state.copy(
            songs = listOf(
                current,
                song(2, title = "Song", sampleRate = 96_000),
                song(3, title = "Other", sampleRate = 192_000),
            )
        )

        facade.updateSameNameSongs(current)

        assertEquals(listOf(2, 1), state.sameNameSongs.map { it.id })
    }

    @Test
    fun switchToVersionPlaysExistingQueueItem() {
        val target = song(2, playable = true)
        state = state.copy(playQueue = PlayQueue().setQueue(listOf(song(1), target), startIndex = 0))

        facade.switchToVersion(target)

        assertEquals(1, state.playQueue.currentIndex)
        assertEquals(1, playedQueueIndex)
        assertTrue(saved)
    }

    @Test
    fun switchToVersionInsertsMissingSongAndPlaysNext() {
        val target = song(3, playable = true)
        state = state.copy(playQueue = PlayQueue().setQueue(listOf(song(1), song(2)), startIndex = 0))

        facade.switchToVersion(target)

        assertEquals(listOf(1, 3, 2), state.playQueue.songs.map { it.id })
        assertTrue(playedNext)
        assertTrue(saved)
    }

    @Test
    fun switchToVersionIgnoresUnplayableSong() {
        state = state.copy(playQueue = PlayQueue().setQueue(listOf(song(1)), startIndex = 0))

        facade.switchToVersion(song(2, playable = false))

        assertEquals(listOf(1), state.playQueue.songs.map { it.id })
        assertFalse(saved)
    }

    @Test
    fun detachCurrentSongUpdatesCurrentSongAndSameNameSongs() {
        val current = song(1, title = "Song")
        val updatedCurrent = current.copy(titleOverride = "Song#1")
        state = state.copy(currentSong = current)
        repositorySongs = listOf(updatedCurrent, song(2, title = "Song"))

        val result = facade.detachSongFromGroup(current)

        assertTrue(result)
        assertEquals(listOf(1 to "Song#1"), groupUpdates)
        assertEquals(updatedCurrent, state.currentSong)
        assertEquals(listOf(1), state.sameNameSongs.map { it.id })
    }

    @Test
    fun failedGroupUpdateDoesNotChangeState() {
        groupUpdateResult = false
        state = state.copy(songs = listOf(song(1)))

        val result = facade.resetSongGroupKey(song(1))

        assertFalse(result)
        assertEquals(listOf(1), state.songs.map { it.id })
    }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int = 0,
        playable: Boolean = false,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = if (playable || sampleRate > 0) sampleRate.coerceAtLeast(44_100) else 0,
            uri = null,
        )
    }
}
