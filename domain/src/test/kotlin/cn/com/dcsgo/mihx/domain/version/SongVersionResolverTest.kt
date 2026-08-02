package cn.com.dcsgo.mihx.domain.version

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SongVersionResolverTest {
    private fun song(id: Long, rate: Int) = Song(
        id = id,
        uri = "u$id",
        title = "同名曲",
        artist = "ar",
        album = "al",
        sampleRate = rate,
    )

    private val resolver = SongVersionResolver()

    @Test
    fun `empty group resolves to null`() {
        assertNull(resolver.resolve(emptyList()))
    }

    @Test
    fun `single version always wins`() {
        val only = song(1, 44_100)
        assertEquals(only, resolver.resolve(listOf(only)))
    }

    @Test
    fun `without preference the highest sample rate wins`() {
        val versions = listOf(song(1, 44_100), song(2, 48_000), song(3, 96_000))
        assertEquals(3L, resolver.resolve(versions)?.id)
    }

    @Test
    fun `user preference wins even over a higher sample rate`() {
        val versions = listOf(song(1, 44_100), song(2, 96_000))
        assertEquals(1L, resolver.resolve(versions, preferredSongId = 1L)?.id)
    }

    @Test
    fun `stale preference falls back to sample rate`() {
        val versions = listOf(song(1, 44_100), song(2, 96_000))
        assertEquals(2L, resolver.resolve(versions, preferredSongId = 99L)?.id)
    }

    @Test
    fun `sample rate ties keep the first version`() {
        val versions = listOf(song(1, 44_100), song(2, 44_100))
        assertEquals(1L, resolver.resolve(versions)?.id)
    }
}
