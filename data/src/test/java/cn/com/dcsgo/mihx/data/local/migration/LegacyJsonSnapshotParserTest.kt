package cn.com.dcsgo.mihx.data.local.migration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyJsonSnapshotParserTest {
    private val parser = LegacyJsonSnapshotParser()

    @Test
    fun parsesSongsPlaylistsAndOverrides() {
        val snapshot = parser.parse(
            songsJson = """
                [
                  {
                    "id": 7,
                    "title": "Bloom",
                    "artist": "Singer",
                    "album": "Sunglow",
                    "sampleRate": 48000,
                    "uri": "content://song/7",
                    "albumArtUri": "content://art/7",
                    "titleOverride": "Bloom Group"
                  }
                ]
            """.trimIndent(),
            playlistsJson = """
                [
                  {"id": 3, "name": "Favorites", "songIds": [7, 8]}
                ]
            """.trimIndent(),
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = emptyMap(),
            nowMs = 1234L,
        )

        assertEquals(1, snapshot.songs.size)
        assertEquals("Bloom", snapshot.songs.single().title)
        assertEquals("Singer", snapshot.songs.single().artist)
        assertEquals("Sunglow", snapshot.songs.single().album)
        assertEquals("content://art/7", snapshot.songs.single().albumArtCacheUri)
        assertEquals(1, snapshot.songGroupOverrides.size)
        assertEquals("Bloom Group", snapshot.songGroupOverrides.single().titleOverride)
        assertEquals("Favorites", snapshot.playlists.single().name)
        assertEquals(listOf(7), snapshot.playlistSongRefs.map { it.songId })
    }

    @Test
    fun parsesPlayStatsFromLegacyPrefPrefixes() {
        val snapshot = parser.parse(
            songsJson = null,
            playlistsJson = null,
            playStatsPrefs = mapOf(
                "play_count_4" to 9,
                "raw_play_count_4" to 12,
                "play_duration_4" to 3_000L,
                "raw_play_count_5" to "2",
            ),
            quickSkipPrefs = emptyMap(),
            nowMs = 1234L,
        )

        assertEquals(2, snapshot.playStats.size)
        val song4 = snapshot.playStats.first { it.songId == 4 }
        assertEquals(9, song4.playCount)
        assertEquals(12, song4.rawPlayCount)
        assertEquals(3_000L, song4.totalDurationMs)
        assertNull(song4.lastPlayedAt)
        val song5 = snapshot.playStats.first { it.songId == 5 }
        assertEquals(0, song5.playCount)
        assertEquals(2, song5.rawPlayCount)
    }

    @Test
    fun parsesQuickSkipSongs() {
        val snapshot = parser.parse(
            songsJson = null,
            playlistsJson = null,
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = mapOf("quick_skip_song_ids_json" to "[11, 12]"),
            nowMs = 5678L,
        )

        assertEquals(listOf(11, 12), snapshot.quickSkipSongs.map { it.songId })
        assertEquals(listOf(5678L, 5678L), snapshot.quickSkipSongs.map { it.addedAt })
    }

    @Test
    fun parsesQuickSkipShortPlayCounts() {
        val snapshot = parser.parse(
            songsJson = null,
            playlistsJson = null,
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = mapOf(
                "short_play_count_4" to 2,
                "short_play_count_5" to "3",
                "short_play_count_6" to 0,
                "short_play_count_bad" to 9,
            ),
            nowMs = 5678L,
        )

        assertEquals(listOf(4, 5), snapshot.quickSkipShortPlayCounts.map { it.songId })
        assertEquals(listOf(2, 3), snapshot.quickSkipShortPlayCounts.map { it.count })
        assertEquals(listOf(5678L, 5678L), snapshot.quickSkipShortPlayCounts.map { it.updatedAt })
    }

    @Test
    fun recoversFromCorruptJsonAndStillParsesStats() {
        val snapshot = parser.parse(
            songsJson = "[not-json",
            playlistsJson = "{bad",
            playStatsPrefs = mapOf("play_count_4" to 2),
            quickSkipPrefs = mapOf("quick_skip_song_ids_json" to "[broken"),
            nowMs = 1234L,
        )

        assertEquals(emptyList<Any>(), snapshot.songs)
        assertEquals(emptyList<Any>(), snapshot.playlists)
        assertEquals(emptyList<Any>(), snapshot.quickSkipSongs)
        assertEquals(1, snapshot.playStats.size)
        assertEquals(2, snapshot.playStats.single().playCount)
    }

    @Test
    fun toleratesMissingOptionalFields() {
        val snapshot = parser.parse(
            songsJson = """
                [
                  {"id": 1, "uri": "content://song/1"},
                  {"title": "missing id"}
                ]
            """.trimIndent(),
            playlistsJson = """
                [
                  {"id": 9},
                  {"name": "missing id"}
                ]
            """.trimIndent(),
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = mapOf("quick_skip_song_ids_json" to """[3, "bad", 4]"""),
            nowMs = 999L,
        )

        assertEquals(1, snapshot.songs.size)
        assertEquals("未知歌曲", snapshot.songs.single().title)
        assertEquals("未知艺术家", snapshot.songs.single().artist)
        assertEquals(1, snapshot.playlists.size)
        assertEquals("未命名歌单", snapshot.playlists.single().name)
        assertEquals(listOf(3, 4), snapshot.quickSkipSongs.map { it.songId })
    }

    @Test
    fun skipsDuplicateSongUrisButKeepsSongsWithoutUris() {
        val snapshot = parser.parse(
            songsJson = """
                [
                  {"id": 1, "title": "First", "uri": "content://song/reused"},
                  {"id": 2, "title": "Duplicate", "uri": "content://song/reused"},
                  {"id": 3, "title": "No Uri A"},
                  {"id": 4, "title": "No Uri B"}
                ]
            """.trimIndent(),
            playlistsJson = null,
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = emptyMap(),
            nowMs = 1234L,
        )

        assertEquals(listOf(1, 3, 4), snapshot.songs.map { it.id })
        assertEquals(listOf("First", "No Uri A", "No Uri B"), snapshot.songs.map { it.title })
    }

    @Test
    fun playlistRefsOnlyPointToMigratedSongsAndAreReindexed() {
        val snapshot = parser.parse(
            songsJson = """
                [
                  {"id": 1, "title": "First", "uri": "content://song/reused"},
                  {"id": 2, "title": "Duplicate", "uri": "content://song/reused"},
                  {"id": 3, "title": "Third", "uri": "content://song/3"}
                ]
            """.trimIndent(),
            playlistsJson = """
                [
                  {"id": 9, "name": "Mixed", "songIds": [2, 3, 99, 3, 1]}
                ]
            """.trimIndent(),
            playStatsPrefs = emptyMap(),
            quickSkipPrefs = emptyMap(),
            nowMs = 1234L,
        )

        assertEquals(listOf(1, 3), snapshot.songs.map { it.id })
        assertEquals(listOf(3, 1), snapshot.playlistSongRefs.map { it.songId })
        assertEquals(listOf(0, 1), snapshot.playlistSongRefs.map { it.sortOrder })
    }
}
