package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.local.dao.AlbumArtistNameRow
import cn.com.dcsgo.mihx.data.local.dao.AlbumCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.ArtistCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.dao.SongEmotionVersion
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite
import cn.com.dcsgo.mihx.data.local.entity.toLite
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSkipSongsRepositoryRoomTest {

    @Test
    fun loadsInitialRoomSongIdsAndAnswersMembership() = runBlocking {
        val dao = FakeMelodyDao().apply {
            quickSkipSongs += listOf(
                QuickSkipSongEntity(songId = 4, addedAt = 1L),
                QuickSkipSongEntity(songId = 2, addedAt = 1L),
            )
        }
        val repository = QuickSkipSongsRepository(melodyDao = dao)

        assertEquals(setOf(2, 4), repository.getSongIds())
        assertTrue(repository.contains(2))
        assertFalse(repository.contains(9))
    }

    @Test
    fun addAndRemovePersistToRoomAndRejectDuplicates() = runBlocking {
        val dao = FakeMelodyDao()
        val repository = QuickSkipSongsRepository(melodyDao = dao)

        assertTrue(repository.add(7))
        assertFalse(repository.add(7))
        assertEquals(setOf(7), repository.getSongIds())
        assertEquals(listOf(7), dao.quickSkipSongs.map { it.songId })

        assertTrue(repository.remove(7))
        assertFalse(repository.remove(7))
        assertEquals(emptySet<Int>(), repository.getSongIds())
        assertEquals(emptyList<Int>(), dao.quickSkipSongs.map { it.songId })
    }

    @Test
    fun shortPlayCountsPersistToRoomAndReset() = runBlocking {
        val dao = FakeMelodyDao().apply {
            quickSkipShortPlayCounts += QuickSkipShortPlayEntity(songId = 3, count = 2, updatedAt = 1L)
        }
        val repository = QuickSkipSongsRepository(melodyDao = dao)

        assertEquals(2, repository.getShortPlayCount(3))
        assertEquals(3, repository.incrementShortPlayCount(3))
        assertEquals(3, dao.quickSkipShortPlayCounts.single { it.songId == 3 }.count)

        repository.resetShortPlayCount(3)

        assertEquals(0, repository.getShortPlayCount(3))
        assertEquals(emptyList<QuickSkipShortPlayEntity>(), dao.quickSkipShortPlayCounts)
    }

    private class FakeMelodyDao : MelodyDao {
        val songs = mutableListOf<SongEntity>()
        val playlists = mutableListOf<PlaylistEntity>()
        val playlistSongRefs = mutableListOf<PlaylistSongCrossRef>()
        val songGroupOverrides = mutableListOf<SongGroupOverrideEntity>()
        val playStats = mutableListOf<PlayStatsEntity>()
        val quickSkipSongs = mutableListOf<QuickSkipSongEntity>()
        val quickSkipShortPlayCounts = mutableListOf<QuickSkipShortPlayEntity>()
        val migrationStates = mutableListOf<MigrationStateEntity>()
        val artists = mutableListOf<ArtistEntity>()
        val albums = mutableListOf<AlbumEntity>()
        val songArtistRefs = mutableListOf<SongArtistCrossRef>()

        override suspend fun songs(): List<SongEntity> = songs

        override suspend fun songIds(): List<Int> = songs.map { it.id }
        override suspend fun songAlbumIds(): List<cn.com.dcsgo.mihx.data.local.dao.SongAlbumIdRow> =
            songs.map { cn.com.dcsgo.mihx.data.local.dao.SongAlbumIdRow(it.id, it.albumId) }

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
        override suspend fun quickSkipSongs(): List<QuickSkipSongEntity> = quickSkipSongs.sortedBy { it.songId }
        override suspend fun quickSkipSong(songId: Int): QuickSkipSongEntity? =
            quickSkipSongs.firstOrNull { it.songId == songId }
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
            this.songs.upsertItems(songs) { it.id }
        }

        override suspend fun upsertPlaylists(playlists: List<PlaylistEntity>) {
            this.playlists.upsertItems(playlists) { it.id }
        }

        override suspend fun upsertPlaylistSongs(refs: List<PlaylistSongCrossRef>) {
            playlistSongRefs.upsertItems(refs) { it.playlistId to it.songId }
        }

        override suspend fun upsertPlayStats(stats: List<PlayStatsEntity>) {
            playStats.upsertItems(stats) { it.songId }
        }

        override suspend fun upsertPlayStat(stat: PlayStatsEntity) {
            playStats.upsertItems(listOf(stat)) { it.songId }
        }

        override suspend fun incrementPlayCount(songId: Int, now: Long) {
            val i = playStats.indexOfFirst { it.songId == songId }
            if (i >= 0) playStats[i] = playStats[i].copy(playCount = playStats[i].playCount + 1, lastPlayedAt = now)
            else playStats += PlayStatsEntity(songId, 1, 0, 0L, now)
        }

        override suspend fun incrementRawPlayCount(songId: Int, now: Long) {
            val i = playStats.indexOfFirst { it.songId == songId }
            if (i >= 0) playStats[i] = playStats[i].copy(rawPlayCount = playStats[i].rawPlayCount + 1, lastPlayedAt = now)
            else playStats += PlayStatsEntity(songId, 0, 1, 0L, now)
        }

        override suspend fun addPlayDuration(songId: Int, durationMs: Long, now: Long) {
            val i = playStats.indexOfFirst { it.songId == songId }
            if (i >= 0) playStats[i] = playStats[i].copy(totalDurationMs = playStats[i].totalDurationMs + durationMs, lastPlayedAt = now)
            else playStats += PlayStatsEntity(songId, 0, 0, durationMs, now)
        }

        override suspend fun upsertQuickSkipSongs(songs: List<QuickSkipSongEntity>) {
            quickSkipSongs.upsertItems(songs) { it.songId }
        }

        override suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity) {
            quickSkipSongs.upsertItems(listOf(song)) { it.songId }
        }

        override suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity) {
            quickSkipShortPlayCounts.upsertItems(listOf(count)) { it.songId }
        }

        /** 原子自增短播放计数（评审 2026-10-27 · M-22 新增 DAO 方法）。 */
        override suspend fun ensureQuickSkipShortPlay(songId: Int, now: Long) = Unit
        override suspend fun incrementQuickSkipShortPlay(songId: Int, now: Long) {
            val existing = quickSkipShortPlayCounts.firstOrNull { it.songId == songId }
            if (existing == null) {
                quickSkipShortPlayCounts += QuickSkipShortPlayEntity(songId = songId, count = 1, updatedAt = now)
            } else {
                val index = quickSkipShortPlayCounts.indexOf(existing)
                quickSkipShortPlayCounts[index] = existing.copy(
                    count = (existing.count.toLong() + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    updatedAt = now,
                )
            }
        }

        override suspend fun ensureWordPreference(word: String, now: Long) = Unit

        override suspend fun addWordVotesDelta(
            word: String,
            likeDelta: Int,
            dislikeDelta: Int,
            now: Long,
        ) = Unit

        override suspend fun upsertSongGroupOverrides(overrides: List<SongGroupOverrideEntity>) {
            songGroupOverrides.upsertItems(overrides) { it.songId }
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
            quickSkipSongs.removeIf { it.songId == songId }
        }

        override suspend fun deleteQuickSkipShortPlay(songId: Int) {
            quickSkipShortPlayCounts.removeIf { it.songId == songId }
        }

        override suspend fun deletePlayStatsForSongs(songIds: List<Int>) {
            playStats.removeIf { it.songId in songIds }
        }

        override suspend fun deletePlaybackEventsForSongs(songIds: List<Int>) {
            // 测试 Fake 不建模 playback_events 表
        }
        override suspend fun deletePlaybackEventsBefore(beforeMs: Long): Int = 0

        override suspend fun emotionTableStat(): cn.com.dcsgo.mihx.domain.repository.EmotionTableStat =
            cn.com.dcsgo.mihx.domain.repository.EmotionTableStat(count = 0, maxAnalyzedAt = 0L)

        override suspend fun emotionRecentAnalyzedTimestamps(limit: Int): List<Long> = emptyList()

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

        override suspend fun migrationState(name: String): MigrationStateEntity? =
            migrationStates.firstOrNull { it.name == name }

        override suspend fun upsertMigrationState(state: MigrationStateEntity) {
            migrationStates.upsertItems(listOf(state)) { it.name }
        }

        val songEmotions = mutableListOf<SongEmotionEntity>()

        override suspend fun songEmotion(songId: Int): SongEmotionEntity? =
            songEmotions.firstOrNull { it.songId == songId }

        override suspend fun allSongEmotions(): List<SongEmotionLite> = songEmotions.map { it.toLite() }

        override fun observeAllSongEmotions(): Flow<List<SongEmotionLite>> =
            flow { emit(songEmotions.map { it.toLite() }) }

        override suspend fun maxKnownSongId(): Int? = listOf(
            songs.maxOfOrNull { it.id },
            playStats.maxOfOrNull { it.songId },
            quickSkipSongs.maxOfOrNull { it.songId },
            quickSkipShortPlayCounts.maxOfOrNull { it.songId },
            songEmotions.maxOfOrNull { it.songId },
        ).filterNotNull().maxOrNull()

        override suspend fun songIdsMissingVocalScore(): List<Int> = emptyList()
        override suspend fun songEmotionVersions(): List<SongEmotionVersion> =
            songEmotions.map { SongEmotionVersion(it.songId, it.modelVersion) }


        override suspend fun emotionCorrectionCount(): Int =
            songEmotions.count { !it.userTags.isNullOrEmpty() }


        override suspend fun insertUserOnlyCorrectionIfAbsent(
            songId: Int, tags: String, edited: Boolean?, now: Long,
        ) = Unit

        override suspend fun upsertSongEmotion(emotion: SongEmotionEntity) {
            songEmotions.removeAll { it.songId == emotion.songId }
            songEmotions.add(emotion)
        }

        override suspend fun updateSongEmotionPreservingUserTags(
            songId: Int, valence: Float, arousal: Float, curveJson: String, peakSec: Float,
            windowsAnalyzed: Int, durationSec: Float, modelVersion: String, analyzedAt: Long,
            embeddingB64: String?, userValence: Float?, userArousal: Float?,
            userTags: String?, userTagsEdited: Boolean?, vocalScore: Float?,
        ): Int = 0

        override suspend fun deleteSongEmotion(songId: Int) {
            songEmotions.removeAll { it.songId == songId }
        }

        override suspend fun clearSongEmotionCorrection(songId: Int) {
            val idx = songEmotions.indexOfFirst { it.songId == songId }
            if (idx >= 0) songEmotions[idx] = songEmotions[idx].copy(
                userValence = null, userArousal = null, userTags = null
            )
        }

        // ── word_preferences（2026-10-20 探索播放；本测试不使用）──
        override fun observeAllWordPreferences():
            kotlinx.coroutines.flow.Flow<List<cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity>> =
            kotlinx.coroutines.flow.emptyFlow()
        override suspend fun allWordPreferences(): List<cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity> =
            emptyList()
        override suspend fun upsertWordPreferences(items: List<cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity>) =
            Unit
        override suspend fun clearWordPreferences() = Unit

        override suspend fun deleteSongEmotionsFor(songIds: List<Int>) {
            songEmotions.removeAll { it.songId in songIds }
        }

        override suspend fun updateSongEmotionCorrection(
            songId: Int, tags: String, edited: Boolean?
        ): Int {
            val idx = songEmotions.indexOfFirst { it.songId == songId }
            if (idx >= 0) songEmotions[idx] = songEmotions[idx].copy(
                userValence = null, userArousal = null, userTags = tags, userTagsEdited = edited
            )
            return if (idx >= 0) 1 else 0
        }

        private fun <T, K> MutableList<T>.upsertItems(
            values: List<T>,
            key: (T) -> K,
        ) {
            values.forEach { value ->
                val index = indexOfFirst { key(it) == key(value) }
                if (index >= 0) {
                    this[index] = value
                } else {
                    add(value)
                }
            }
        }
    }
}
