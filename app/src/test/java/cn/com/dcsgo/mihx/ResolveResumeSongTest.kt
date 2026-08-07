package cn.com.dcsgo.mihx

import cn.com.dcsgo.mihx.app.resolveResumeSong
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResolveResumeSongTest {

    @Test
    fun resolvesPlayableSongInPlaylist() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")
        val resume = PlaylistResume(songId = 1, updatedAtMs = 1_000L)

        val resolved = resolveResumeSong(resume, listOf(song), setOf(1), isPlayable = { true })

        assertEquals(song, resolved)
    }

    @Test
    fun returnsNullWhenSongNotInPlaylist() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")
        val resume = PlaylistResume(songId = 1, updatedAtMs = 1_000L)

        val resolved = resolveResumeSong(resume, listOf(song), setOf(2), isPlayable = { true })

        assertNull(resolved)
    }

    @Test
    fun returnsNullWhenSongNotPlayable() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")
        val resume = PlaylistResume(songId = 1, updatedAtMs = 1_000L)

        val resolved = resolveResumeSong(resume, listOf(song), setOf(1), isPlayable = { false })

        assertNull(resolved)
    }

    @Test
    fun returnsNullWhenResumeIsNull() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")

        val resolved = resolveResumeSong(null, listOf(song), setOf(1), isPlayable = { true })

        assertNull(resolved)
    }

    @Test
    fun defaultIsPlayableRequiresUriAndUriIsNullReturnsNull() {
        val song = Song(id = 1, title = "晴天", artist = "周杰伦")
        val resume = PlaylistResume(songId = 1, updatedAtMs = 1_000L)

        // JVM 单测中 Song.uri 一律为 null，默认 isPlayable({ it.uri != null }) 应返回 null
        val resolved = resolveResumeSong(resume, listOf(song), setOf(1))

        assertNull(resolved)
    }
}
