package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongGroupCoordinatorTest {

    private val updates = mutableListOf<Pair<Int, String?>>()
    private var updateResult = true
    private var repositorySongs = emptyList<Song>()
    private val coordinator = SongGroupCoordinator(
        updateSongTitleOverride = { songId, titleOverride ->
            updates += songId to titleOverride
            updateResult
        },
        getSongs = { repositorySongs },
        isPlayable = { true },
    )

    @Test
    fun detachUsesUniqueGroupKeyAndRefreshesCurrentSong() {
        val original = song(1, title = "Song", sampleRate = 44_100)
        val updated = original.copy(titleOverride = "Song#1")
        repositorySongs = listOf(
            updated,
            song(2, title = "Song", sampleRate = 96_000),
        )

        val result = coordinator.detachFromGroup(original, currentSongId = 1)

        assertTrue(result.updated)
        assertEquals(listOf(1 to "Song#1"), updates)
        assertEquals(updated, result.currentSong)
        assertEquals(listOf(1), result.sameNameSongs?.map { it.id })
        assertEquals(repositorySongs, result.songs)
    }

    @Test
    fun reassignUsesTargetGroupKey() {
        val song = song(1, title = "First")
        val target = song(2, title = "Target", titleOverride = "Shared")
        repositorySongs = listOf(song.copy(titleOverride = "Shared"), target)

        val result = coordinator.reassignToGroup(song, target)

        assertTrue(result.updated)
        assertEquals(listOf(1 to "Shared"), updates)
        assertNull(result.currentSong)
        assertEquals(repositorySongs, result.songs)
    }

    @Test
    fun resetClearsGroupKey() {
        val song = song(1, title = "Song", titleOverride = "Custom")
        repositorySongs = listOf(song.copy(titleOverride = null))

        val result = coordinator.resetGroupKey(song)

        assertTrue(result.updated)
        assertEquals(listOf(1 to null), updates)
        assertEquals(repositorySongs, result.songs)
    }

    @Test
    fun failedUpdateDoesNotReadRepositorySnapshot() {
        updateResult = false
        repositorySongs = listOf(song(1))

        val result = coordinator.resetGroupKey(song(1))

        assertFalse(result.updated)
        assertEquals(listOf(1 to null), updates)
        assertEquals(emptyList<Song>(), result.songs)
        assertNull(result.currentSong)
    }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int = 44_100,
        titleOverride: String? = null,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = sampleRate,
            titleOverride = titleOverride,
        )
    }
}
