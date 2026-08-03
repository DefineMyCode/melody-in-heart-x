package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVersionManagerTest {

    @Test
    fun groupsPlayableSongsByGroupKey() {
        val songs = listOf(
            song(1, title = "A"),
            song(2, title = "A"),
            song(3, title = "B"),
            song(4, title = "C", playable = false),
        )

        val groups = SongVersionManager.groupedSongs(songs) { it.sampleRate > 0 }

        assertEquals(listOf(listOf(1, 2), listOf(3)), groups.map { group -> group.map { it.id } })
    }

    @Test
    fun sortsSameNameSongsBySampleRateDescending() {
        val current = song(1, title = "A", sampleRate = 44_100)
        val songs = listOf(
            current,
            song(2, title = "A", sampleRate = 96_000),
            song(3, title = "A", sampleRate = 48_000),
            song(4, title = "B", sampleRate = 192_000),
        )

        val sameNameSongs = SongVersionManager.sortedSameNameSongs(current, songs) { it.sampleRate > 0 }

        assertEquals(listOf(2, 3, 1), sameNameSongs.map { it.id })
    }

    @Test
    fun switchToVersionPlaysExistingSongInQueue() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0)

        val plan = SongVersionManager.switchToVersion(queue, song(3))

        assertTrue(plan is SongVersionManager.SwitchPlan.PlayExisting)
        val existing = plan as SongVersionManager.SwitchPlan.PlayExisting
        assertEquals(2, existing.index)
        assertEquals(2, existing.queue.currentIndex)
    }

    @Test
    fun switchToVersionInsertsMissingSongAfterCurrent() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1)

        val plan = SongVersionManager.switchToVersion(queue, song(4))

        assertTrue(plan is SongVersionManager.SwitchPlan.InsertNext)
        val insert = plan as SongVersionManager.SwitchPlan.InsertNext
        assertEquals(listOf(1, 2, 4, 3), insert.queue.songs.map { it.id })
        assertEquals(1, insert.queue.currentIndex)
    }

    @Test
    fun replaceCurrentUsesExistingTargetIndexWhenPresent() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 0)

        val newQueue = SongVersionManager.replaceCurrentInQueue(queue, song(3))

        assertEquals(2, newQueue?.currentIndex)
        assertEquals(listOf(1, 2, 3), newQueue?.songs?.map { it.id })
    }

    @Test
    fun replaceCurrentSwapsCurrentSongWhenTargetIsMissing() {
        val queue = PlayQueue().setQueue(songs(1, 2, 3), startIndex = 1)

        val newQueue = SongVersionManager.replaceCurrentInQueue(queue, song(4))

        assertEquals(listOf(1, 4, 3), newQueue?.songs?.map { it.id })
        assertEquals(1, newQueue?.currentIndex)
    }

    @Test
    fun detachedGroupKeyUsesTitleAndId() {
        assertEquals("Song#7", SongVersionManager.detachedGroupKey(song(7, title = "Song")))
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int = 44_100,
        playable: Boolean = true,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = if (playable) sampleRate else 0,
        )
    }
}
