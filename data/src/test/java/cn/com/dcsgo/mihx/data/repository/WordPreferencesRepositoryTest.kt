package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.WordPreferenceKind
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 情绪词条收听偏好计票（2026-10-20 探索播放）：
 * 验证 recordSongOutcome 的票数累计与归属判定（计票制，单次跳过不翻转已喜欢的词条）。
 */
class WordPreferencesRepositoryTest {

    @Test
    fun recordSongOutcome_incrementsDislikeVotesOnSkip() = runBlocking {
        val dao = FakeMelodyDao()
        dao.upsertWordPreferences(
            listOf(WordPreferenceEntity("静谧", likeVotes = 3, dislikeVotes = 1, updatedAt = 1L)),
        )
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(words = listOf("静谧", "伤感"), liked = false)

        val quiet = repo.getAll()["静谧"]!!
        assertEquals(3, quiet.likeVotes)
        assertEquals(2, quiet.dislikeVotes) // 跳过：不喜欢票 +1，仍为喜欢（3>2，不翻转）
        assertEquals(WordPreferenceKind.LIKE, quiet.kind)
        assertEquals(1, repo.getAll()["伤感"]!!.dislikeVotes)
        assertEquals(WordPreferenceKind.DISLIKE, repo.getAll()["伤感"]!!.kind)
    }

    @Test
    fun recordSongOutcome_incrementsLikeVotesOnFullListen() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(words = listOf("快乐", "治愈"), liked = true)

        val map = repo.getAll()
        assertEquals(1, map["快乐"]!!.likeVotes)
        assertEquals(0, map["快乐"]!!.dislikeVotes)
        assertEquals(WordPreferenceKind.LIKE, map["快乐"]!!.kind)
        assertEquals(WordPreferenceKind.LIKE, map["治愈"]!!.kind)
    }

    @Test
    fun recordSongOutcome_accumulatesAcrossSessionsAndStaysNeutralAtTie() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(words = listOf("静谧"), liked = true)
        repo.recordSongOutcome(words = listOf("静谧"), liked = false)

        val quiet = repo.getAll()["静谧"]!!
        assertEquals(1, quiet.likeVotes)
        assertEquals(1, quiet.dislikeVotes)
        // 平票 → 中立：既不算喜欢也不算不喜欢（补充过滤时忽略）
        assertEquals(WordPreferenceKind.NEUTRAL, quiet.kind)
    }

    @Test
    fun recordSongOutcome_ignoresEmptyWords() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(words = emptyList(), liked = true)

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun clearAll_removesAllWordPreferences() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(words = listOf("静谧", "伤感"), liked = true)
        repo.recordSongOutcome(words = listOf("燃"), liked = false)
        assertEquals(3, repo.getAll().size)

        repo.clearAll()

        assertTrue(repo.getAll().isEmpty())
        assertTrue(dao.allWordPreferences().isEmpty())
    }

    private class FakeMelodyDao : MelodyDao {
        private val words = MutableStateFlow<List<WordPreferenceEntity>>(emptyList())

        override fun observeAllWordPreferences(): Flow<List<WordPreferenceEntity>> = words.asStateFlow()

        override suspend fun allWordPreferences(): List<WordPreferenceEntity> = words.value

        override suspend fun upsertWordPreferences(items: List<WordPreferenceEntity>) {
            val existing = words.value.associateBy { it.word }
            val updated = items.map { item ->
                existing[item.word]?.copy(
                    likeVotes = item.likeVotes,
                    dislikeVotes = item.dislikeVotes,
                    updatedAt = item.updatedAt,
                ) ?: item
            }
            val merged = (existing.values.filter { old -> updated.none { it.word == old.word } }) + updated
            words.value = merged
        }

        override suspend fun clearWordPreferences() {
            words.value = emptyList()
        }

        override suspend fun songs(): List<cn.com.dcsgo.mihx.data.local.entity.SongEntity> = emptyList()
        override suspend fun songCount(): Int = 0
        override suspend fun playlists(): List<cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity> = emptyList()
        override suspend fun playlistSongRefs(): List<cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef> = emptyList()
        override suspend fun songGroupOverrides(): List<cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity> = emptyList()
        override suspend fun playStats(): List<cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity> = emptyList()
        override suspend fun playStat(songId: Int): cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity? = null
        override suspend fun playStatsIn(songIds: List<Int>): List<cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity> = emptyList()
        override suspend fun insertPlaybackEvent(event: cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity) = Unit
        override suspend fun totalDurationBetween(startMs: Long, endMs: Long): Long = 0L
        override suspend fun distinctSongsBetween(startMs: Long, endMs: Long): Int = 0
        override suspend fun dailyDurationsBetween(startMs: Long, endMs: Long): List<cn.com.dcsgo.mihx.data.local.dao.DailyDurationRow> = emptyList()
        override suspend fun playCountsBetween(startMs: Long, endMs: Long): List<cn.com.dcsgo.mihx.data.local.dao.SongPlayCountRow> = emptyList()
        override suspend fun quickSkipSongs(): List<cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity> = emptyList()
        override suspend fun quickSkipSong(songId: Int): cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity? = null
        override suspend fun quickSkipShortPlay(songId: Int): cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity? = null
        override suspend fun artists(): List<cn.com.dcsgo.mihx.data.local.entity.ArtistEntity> = emptyList()
        override suspend fun albums(): List<cn.com.dcsgo.mihx.data.local.entity.AlbumEntity> = emptyList()
        override suspend fun songArtistRefs(): List<cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef> = emptyList()
        override suspend fun artistIdsForSong(songId: Int): List<Int> = emptyList()
        override suspend fun artistCatalog(): List<cn.com.dcsgo.mihx.data.local.dao.ArtistCatalogRow> = emptyList()
        override suspend fun albumCatalog(): List<cn.com.dcsgo.mihx.data.local.dao.AlbumCatalogRow> = emptyList()
        override suspend fun albumArtistNames(): List<cn.com.dcsgo.mihx.data.local.dao.AlbumArtistNameRow> = emptyList()
        override suspend fun upsertSongs(songs: List<cn.com.dcsgo.mihx.data.local.entity.SongEntity>) = Unit
        override suspend fun upsertPlaylists(playlists: List<cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity>) = Unit
        override suspend fun upsertPlaylistSongs(refs: List<cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef>) = Unit
        override suspend fun upsertPlayStats(stats: List<cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity>) = Unit
        override suspend fun upsertPlayStat(stat: cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity) = Unit
        override suspend fun incrementPlayCount(songId: Int, now: Long) = Unit
        override suspend fun incrementRawPlayCount(songId: Int, now: Long) = Unit
        override suspend fun addPlayDuration(songId: Int, durationMs: Long, now: Long) = Unit
        override suspend fun upsertQuickSkipSongs(songs: List<cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity>) = Unit
        override suspend fun upsertQuickSkipSong(song: cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity) = Unit
        override suspend fun upsertQuickSkipShortPlay(count: cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity) = Unit
        override suspend fun upsertSongGroupOverrides(overrides: List<cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity>) = Unit
        override suspend fun insertArtists(artists: List<cn.com.dcsgo.mihx.data.local.entity.ArtistEntity>) = Unit
        override suspend fun insertAlbums(albums: List<cn.com.dcsgo.mihx.data.local.entity.AlbumEntity>) = Unit
        override suspend fun insertSongArtistRefs(refs: List<cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef>) = Unit
        override suspend fun deleteAllSongs() = Unit
        override suspend fun deleteAllSongGroupOverrides() = Unit
        override suspend fun deleteAllPlaylists() = Unit
        override suspend fun deleteAllPlaylistSongRefs() = Unit
        override suspend fun deleteQuickSkipSong(songId: Int) = Unit
        override suspend fun deleteQuickSkipShortPlay(songId: Int) = Unit
        override suspend fun deletePlayStatsForSongs(songIds: List<Int>) = Unit
        override suspend fun deletePlaybackEventsForSongs(songIds: List<Int>) = Unit
        override suspend fun deleteQuickSkipSongsFor(songIds: List<Int>) = Unit
        override suspend fun deleteQuickSkipShortPlaysFor(songIds: List<Int>) = Unit
        override suspend fun deleteSongEmotionsFor(songIds: List<Int>) = Unit
        override suspend fun deleteAllSongArtistRefs() = Unit
        override suspend fun deleteAllArtists() = Unit
        override suspend fun deleteAllAlbums() = Unit
        override suspend fun deleteOrphanArtists() = Unit
        override suspend fun deleteOrphanAlbums() = Unit
        override suspend fun replaceSongs(
            songs: List<cn.com.dcsgo.mihx.data.local.entity.SongEntity>,
            overrides: List<cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity>,
        ) = Unit
        override suspend fun replacePlaylists(
            playlists: List<cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity>,
            refs: List<cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef>,
        ) = Unit
        override suspend fun syncLibraryCatalog(songs: List<cn.com.dcsgo.mihx.core.model.Song>) = Unit
        override suspend fun migrationState(name: String): cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity? = null
        override suspend fun upsertMigrationState(state: cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity) = Unit
        override suspend fun songEmotion(songId: Int): cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity? = null
        override suspend fun allSongEmotions(): List<cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite> = emptyList()
        override fun observeAllSongEmotions(): Flow<List<cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite>> =
            MutableStateFlow<List<cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite>>(emptyList()).asStateFlow()
        override suspend fun maxKnownSongId(): Int? = null
        override suspend fun songEmotionVersions(): List<cn.com.dcsgo.mihx.data.local.dao.SongEmotionVersion> = emptyList()
        override suspend fun emotionCorrectionCount(): Int = 0
        override suspend fun emotionAnalyzedTimeline(): List<Long> = emptyList()
        override suspend fun upsertSongEmotion(emotion: cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity) = Unit
        override suspend fun updateSongEmotionCorrection(
            songId: Int, tags: String, edited: Boolean?
        ) = Unit
        override suspend fun upsertUserOnlyCorrection(emotion: cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity) = Unit
        override suspend fun deleteSongEmotion(songId: Int) = Unit
        override suspend fun clearSongEmotionCorrection(songId: Int) = Unit
    }
}
