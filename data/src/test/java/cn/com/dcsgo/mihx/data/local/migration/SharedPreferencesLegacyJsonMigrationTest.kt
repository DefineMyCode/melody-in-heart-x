package cn.com.dcsgo.mihx.data.local.migration

import android.content.SharedPreferences
import cn.com.dcsgo.mihx.data.local.dao.AlbumArtistNameRow
import cn.com.dcsgo.mihx.data.local.dao.AlbumCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.ArtistCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.dao.SongEmotionVersion
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.AlbumEntity
import cn.com.dcsgo.mihx.data.local.entity.ArtistEntity
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPreferencesLegacyJsonMigrationTest {
    @Test
    fun migratesLegacyJsonIntoDaoAndWritesCompletionMarker() = runBlocking {
        val prefs = fakePrefs(
            prefsByName = mapOf(
                LEGACY_PREFS_NAME to FakeSharedPreferences(
                    mapOf(
                        KEY_SONGS_JSON to """
                            [
                              {
                                "id": 7,
                                "title": "Bloom",
                                "artist": "Singer",
                                "sampleRate": 48000,
                                "uri": "content://song/7",
                                "albumArtUri": "content://art/7",
                                "titleOverride": "Bloom Group"
                              }
                            ]
                        """.trimIndent(),
                        KEY_PLAYLISTS_JSON to """[{"id": 3, "name": "Favorites", "songIds": [7]}]""",
                    )
                ),
                PLAY_STATS_PREFS_NAME to FakeSharedPreferences(
                    mapOf(
                        "play_count_7" to 2,
                        "raw_play_count_7" to 5,
                        "play_duration_7" to 12_000L,
                    )
                ),
                QUICK_SKIP_PREFS_NAME to FakeSharedPreferences(
                    mapOf(
                        KEY_QUICK_SKIP_SONG_IDS_JSON to "[7]",
                        "short_play_count_7" to 2,
                    )
                ),
            )
        )
        val dao = FakeMelodyDao()

        SharedPreferencesLegacyJsonMigration(
            dao = dao,
            clockMs = { 1234L },
            sharedPreferencesProvider = prefs,
        ).migrateIfNeeded()

        assertEquals(listOf(7), dao.songs.map { it.id })
        assertEquals("Bloom", dao.songs.single().title)
        assertEquals("content://art/7", dao.songs.single().albumArtCacheUri)
        assertEquals(null, dao.songs.single().lrcUri)
        assertEquals("Bloom Group", dao.songGroupOverrides.single().titleOverride)
        assertEquals("Favorites", dao.playlists.single().name)
        assertEquals(PlaylistSongCrossRef(playlistId = 3, songId = 7, sortOrder = 0), dao.playlistSongRefs.single())
        assertEquals(PlayStatsEntity(songId = 7, playCount = 2, rawPlayCount = 5, totalDurationMs = 12_000L, lastPlayedAt = null), dao.playStats.single())
        assertEquals(QuickSkipSongEntity(songId = 7, addedAt = 1234L), dao.quickSkipSongs.single())
        assertEquals(QuickSkipShortPlayEntity(songId = 7, count = 2, updatedAt = 1234L), dao.quickSkipShortPlayCounts.single())
        assertEquals(MigrationStateEntity(LEGACY_MIGRATION_NAME, completedAt = 1234L), dao.migrationState(LEGACY_MIGRATION_NAME))
    }

    @Test
    fun corruptJsonStillMigratesStatsAndWritesCompletionMarker() = runBlocking {
        val prefs = fakePrefs(
            prefsByName = mapOf(
                LEGACY_PREFS_NAME to FakeSharedPreferences(
                    mapOf(
                        KEY_SONGS_JSON to "[bad",
                        KEY_PLAYLISTS_JSON to "{bad",
                    )
                ),
                PLAY_STATS_PREFS_NAME to FakeSharedPreferences(mapOf("play_count_4" to 9)),
                QUICK_SKIP_PREFS_NAME to FakeSharedPreferences(mapOf(KEY_QUICK_SKIP_SONG_IDS_JSON to "[bad")),
            )
        )
        val dao = FakeMelodyDao()

        SharedPreferencesLegacyJsonMigration(
            dao = dao,
            clockMs = { 2222L },
            sharedPreferencesProvider = prefs,
        ).migrateIfNeeded()

        assertEquals(emptyList<SongEntity>(), dao.songs)
        assertEquals(emptyList<PlaylistEntity>(), dao.playlists)
        assertEquals(emptyList<QuickSkipSongEntity>(), dao.quickSkipSongs)
        assertEquals(PlayStatsEntity(songId = 4, playCount = 9, rawPlayCount = 0, totalDurationMs = 0L, lastPlayedAt = null), dao.playStats.single())
        assertEquals(MigrationStateEntity(LEGACY_MIGRATION_NAME, completedAt = 2222L), dao.migrationState(LEGACY_MIGRATION_NAME))
    }

    @Test
    fun existingCompletionMarkerSkipsMigration() = runBlocking {
        val prefs = fakePrefs(
            prefsByName = mapOf(
                LEGACY_PREFS_NAME to FakeSharedPreferences(
                    mapOf(KEY_SONGS_JSON to """[{"id": 1, "title": "Skipped"}]""")
                )
            )
        )
        val dao = FakeMelodyDao().apply {
            migrationStates[LEGACY_MIGRATION_NAME] = MigrationStateEntity(LEGACY_MIGRATION_NAME, completedAt = 1L)
        }

        SharedPreferencesLegacyJsonMigration(
            dao = dao,
            clockMs = { 9999L },
            sharedPreferencesProvider = prefs,
        ).migrateIfNeeded()

        assertEquals(emptyList<SongEntity>(), dao.songs)
        assertEquals(0, dao.migrationStateWriteCount)
        assertEquals(MigrationStateEntity(LEGACY_MIGRATION_NAME, completedAt = 1L), dao.migrationState(LEGACY_MIGRATION_NAME))
    }

    private fun fakePrefs(
        prefsByName: Map<String, SharedPreferences>,
    ): (String) -> SharedPreferences {
        return { name -> prefsByName[name] ?: FakeSharedPreferences() }
    }

    private class FakeSharedPreferences(
        private val values: Map<String, Any?> = emptyMap(),
    ) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? {
            return values[key] as? String ?: defValue
        }

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("Not needed in migration tests")
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }

    private class FakeMelodyDao : MelodyDao {
        val songs = mutableListOf<SongEntity>()
        val playlists = mutableListOf<PlaylistEntity>()
        val playlistSongRefs = mutableListOf<PlaylistSongCrossRef>()
        val songGroupOverrides = mutableListOf<SongGroupOverrideEntity>()
        val playStats = mutableListOf<PlayStatsEntity>()
        val quickSkipSongs = mutableListOf<QuickSkipSongEntity>()
        val quickSkipShortPlayCounts = mutableListOf<QuickSkipShortPlayEntity>()
        val migrationStates = mutableMapOf<String, MigrationStateEntity>()
        val artists = mutableListOf<ArtistEntity>()
        val albums = mutableListOf<AlbumEntity>()
        val songArtistRefs = mutableListOf<SongArtistCrossRef>()
        var migrationStateWriteCount = 0

        override suspend fun songs(): List<SongEntity> = songs

        override suspend fun songCount(): Int = songs.size
        override suspend fun playlists(): List<PlaylistEntity> = playlists
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> = playlistSongRefs
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats
        override suspend fun playStatsIn(songIds: List<Int>): List<PlayStatsEntity> =
            playStats.filter { it.songId in songIds }
        override suspend fun insertPlaybackEvent(
            event: cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity,
        ) = Unit
        override suspend fun totalDurationBetween(startMs: Long, endMs: Long): Long = 0L
        override suspend fun distinctSongsBetween(startMs: Long, endMs: Long): Int = 0
        override suspend fun dailyDurationsBetween(
            startMs: Long,
            endMs: Long,
        ): List<cn.com.dcsgo.mihx.data.local.dao.DailyDurationRow> = emptyList()
        override suspend fun playCountsBetween(
            startMs: Long,
            endMs: Long,
        ): List<cn.com.dcsgo.mihx.data.local.dao.SongPlayCountRow> = emptyList()
        override suspend fun playStat(songId: Int): PlayStatsEntity? = playStats.firstOrNull { it.songId == songId }
        override suspend fun quickSkipSongs(): List<QuickSkipSongEntity> = quickSkipSongs
        override suspend fun quickSkipSong(songId: Int): QuickSkipSongEntity? = quickSkipSongs.firstOrNull { it.songId == songId }
        override suspend fun quickSkipShortPlay(songId: Int): QuickSkipShortPlayEntity? =
            quickSkipShortPlayCounts.firstOrNull { it.songId == songId }
        override suspend fun artists(): List<ArtistEntity> = artists.sortedBy { it.name }
        override suspend fun albums(): List<AlbumEntity> = albums.sortedBy { it.name }
        override suspend fun songArtistRefs(): List<SongArtistCrossRef> = songArtistRefs
        override suspend fun artistIdsForSong(songId: Int): List<Int> =
            songArtistRefs.filter { it.songId == songId }.map { it.artistId }

        override suspend fun artistCatalog(): List<ArtistCatalogRow> = emptyList()

        override suspend fun albumCatalog(): List<AlbumCatalogRow> = emptyList()

        override suspend fun albumArtistNames(): List<AlbumArtistNameRow> = emptyList()

        override suspend fun upsertSongs(songs: List<SongEntity>) {
            this.songs.upsertBy(songs) { it.id }
        }

        override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) {
            this.playlists.upsertBy(playlists) { it.id }
        }

        override suspend fun upsertPlaylistSongs(refs: List<PlaylistSongCrossRef>) {
            playlistSongRefs.upsertBy(refs) { it.playlistId to it.songId }
        }

        override suspend fun upsertPlayStats(stats: List<PlayStatsEntity>) {
            playStats.upsertBy(stats) { it.songId }
        }

        override suspend fun upsertPlayStat(stat: PlayStatsEntity) {
            playStats.upsertBy(listOf(stat)) { it.songId }
        }

        override suspend fun upsertQuickSkipSongs(songs: List<QuickSkipSongEntity>) {
            quickSkipSongs.upsertBy(songs) { it.songId }
        }

        override suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity) {
            quickSkipSongs.upsertBy(listOf(song)) { it.songId }
        }

        override suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity) {
            quickSkipShortPlayCounts.upsertBy(listOf(count)) { it.songId }
        }

        override suspend fun upsertSongGroupOverrides(overrides: List<SongGroupOverrideEntity>) {
            songGroupOverrides.upsertBy(overrides) { it.songId }
        }

        override suspend fun insertArtists(artists: List<ArtistEntity>) {
            artists.forEach { artist ->
                if (this.artists.none { it.name == artist.name }) {
                    this.artists += artist.copy(artistId = this.artists.size + 1)
                }
            }
        }

        override suspend fun insertAlbums(albums: List<AlbumEntity>) {
            albums.forEach { album ->
                if (this.albums.none { it.name == album.name }) {
                    this.albums += album.copy(albumId = this.albums.size + 1)
                }
            }
        }

        override suspend fun insertSongArtistRefs(refs: List<SongArtistCrossRef>) {
            refs.forEach { ref ->
                if (songArtistRefs.none { it.songId == ref.songId && it.artistId == ref.artistId }) {
                    songArtistRefs += ref
                }
            }
        }

        override suspend fun deleteAllSongs() {
            songs.clear()
        }

        override suspend fun deleteAllSongGroupOverrides() {
            songGroupOverrides.clear()
        }

        override suspend fun deleteAllPlaylists() {
            playlists.clear()
        }

        override suspend fun deleteAllPlaylistSongRefs() {
            playlistSongRefs.clear()
        }

        override suspend fun deleteQuickSkipSong(songId: Int) {
            quickSkipSongs.removeAll { it.songId == songId }
        }

        override suspend fun deleteQuickSkipShortPlay(songId: Int) {
            quickSkipShortPlayCounts.removeAll { it.songId == songId }
        }

        override suspend fun deletePlayStatsForSongs(songIds: List<Int>) {
            playStats.removeIf { it.songId in songIds }
        }

        override suspend fun deletePlaybackEventsForSongs(songIds: List<Int>) {
            // 测试 Fake 不建模 playback_events 表
        }

        override suspend fun deleteQuickSkipSongsFor(songIds: List<Int>) {
            quickSkipSongs.removeIf { it.songId in songIds }
        }

        override suspend fun deleteQuickSkipShortPlaysFor(songIds: List<Int>) {
            quickSkipShortPlayCounts.removeIf { it.songId in songIds }
        }

        override suspend fun deleteAllSongArtistRefs() {
            songArtistRefs.clear()
        }

        override suspend fun deleteAllArtists() {
            artists.clear()
        }

        override suspend fun deleteAllAlbums() {
            albums.clear()
        }

        override suspend fun deleteOrphanArtists() {
            val usedIds = songArtistRefs.map { it.artistId }.toSet()
            artists.removeIf { it.artistId !in usedIds }
        }

        override suspend fun deleteOrphanAlbums() {
            val usedIds = songs.mapNotNull { it.albumId }.toSet()
            albums.removeIf { it.albumId !in usedIds }
        }

        override suspend fun migrationState(name: String): MigrationStateEntity? = migrationStates[name]

        override suspend fun upsertMigrationState(state: MigrationStateEntity) {
            migrationStates[state.name] = state
            migrationStateWriteCount++
        }

        val songEmotions = mutableMapOf<Int, SongEmotionEntity>()

        override suspend fun songEmotion(songId: Int): SongEmotionEntity? =
            songEmotions[songId]

        override suspend fun allSongEmotions(): List<SongEmotionEntity> =
            songEmotions.values.toList()

        override suspend fun songEmotionVersions(): List<SongEmotionVersion> =
            songEmotions.values.map { SongEmotionVersion(it.songId, it.modelVersion) }

        override suspend fun emotionAnalyzedTimeline(): List<Long> =
            songEmotions.values.filter { it.windowsAnalyzed > 0 }
                .sortedBy { it.analyzedAt }.map { it.analyzedAt }

        override suspend fun emotionCorrectionCount(): Int =
            songEmotions.values.count { it.userValence != null }


        override suspend fun upsertSongEmotion(emotion: SongEmotionEntity) {
            songEmotions[emotion.songId] = emotion
        }

        override suspend fun deleteSongEmotion(songId: Int) {
            songEmotions.remove(songId)
        }

        override suspend fun updateSongEmotionCorrection(songId: Int, v: Float, a: Float, tags: String) {
            songEmotions[songId]?.let {
                songEmotions[songId] = it.copy(userValence = v, userArousal = a, userTags = tags)
            }
        }

        override suspend fun correctedSongEmotions(): List<SongEmotionEntity> =
            songEmotions.values.filter { it.userValence != null && it.embeddingB64 != null }
    }

    private companion object {
        const val LEGACY_MIGRATION_NAME = "shared_preferences_json_v1"
        const val LEGACY_PREFS_NAME = "music_player_prefs"
        const val KEY_PLAYLISTS_JSON = "playlists_json"
        const val KEY_SONGS_JSON = "songs_json"
        const val KEY_QUICK_SKIP_SONG_IDS_JSON = "quick_skip_song_ids_json"
        const val PLAY_STATS_PREFS_NAME = "play_stats_prefs"
        const val QUICK_SKIP_PREFS_NAME = "quick_skip_songs_prefs"

        fun <T, K> MutableList<T>.upsertBy(items: List<T>, key: (T) -> K) {
            items.forEach { item ->
                removeAll { existing -> key(existing) == key(item) }
                add(item)
            }
        }
    }
}
