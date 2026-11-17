package cn.com.dcsgo.mihx.feature.playlist

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.SongSortMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SongSorterTest {

    private fun song(id: Int, bitRateKbps: Int): Song = Song(
        id = id,
        title = "song-$id",
        artist = "artist",
        bitRateKbps = bitRateKbps,
    )

    @Test
    fun `bit rate sorts ascending and descending`() {
        val songs = listOf(song(1, 320), song(2, 128), song(3, 1411))

        assertEquals(
            listOf(2, 1, 3),
            SongSorter.sort(songs, SongSortMode.BIT_RATE, ascending = true).map { it.id },
        )
        assertEquals(
            listOf(3, 1, 2),
            SongSorter.sort(songs, SongSortMode.BIT_RATE, ascending = false).map { it.id },
        )
    }

    @Test
    fun `unknown bit rate stays at the low end in ascending order`() {
        val songs = listOf(song(1, 320), song(2, 0), song(3, 128))

        assertEquals(
            listOf(2, 3, 1),
            SongSorter.sort(songs, SongSortMode.BIT_RATE, ascending = true).map { it.id },
        )
    }
}