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
 *
 * 2026-10-25：票重改为按**词条占比**给（`votes` 是 词 -> 票重），
 * 这里仍用票重 1 验证「累计 + 归属判定」的语义不变，另外单独有一条测加权主次。
 */
class WordPreferencesRepositoryTest {

    @Test
    fun recordSongOutcome_incrementsDislikeVotesOnSkip() = runBlocking {
        val dao = FakeMelodyDao()
        dao.upsertWordPreferences(
            listOf(WordPreferenceEntity("静谧", likeVotes = 3, dislikeVotes = 1, updatedAt = 1L)),
        )
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = mapOf("静谧" to 1, "伤感" to 1), liked = false)

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

        repo.recordSongOutcome(votes = mapOf("快乐" to 1, "治愈" to 1), liked = true)

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

        repo.recordSongOutcome(votes = mapOf("静谧" to 1), liked = true)
        repo.recordSongOutcome(votes = mapOf("静谧" to 1), liked = false)

        val quiet = repo.getAll()["静谧"]!!
        assertEquals(1, quiet.likeVotes)
        assertEquals(1, quiet.dislikeVotes)
        // 平票 → 中立：既不算喜欢也不算不喜欢（补充过滤时忽略）
        assertEquals(WordPreferenceKind.NEUTRAL, quiet.kind)
    }

    @Test
    fun recordSongOutcome_usesTheGivenWeightPerWord() = runBlocking {
        // 票重 = 词条在整首里占的比例（×100）：主情绪票重高、顺带蹭到的次要词票重低
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = mapOf("emo" to 80, "治愈" to 20), liked = false)

        val map = repo.getAll()
        assertEquals(0, map["emo"]!!.likeVotes)
        assertEquals(80, map["emo"]!!.dislikeVotes)
        assertEquals(20, map["治愈"]!!.dislikeVotes)
        // 归属判定是相对比较，票重不影响它
        assertEquals(WordPreferenceKind.DISLIKE, map["emo"]!!.kind)
    }

    @Test
    fun recordSongOutcome_ignoresZeroWeightWords() = runBlocking {
        // 占比极低(票重 0)的词不该留下任何记录 —— 那不是"这个情绪", 只是碰巧落在附近
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = mapOf("emo" to 0, "治愈" to 0), liked = true)

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun recordSongOutcome_ignoresEmptyWords() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = emptyMap(), liked = true)

        assertTrue(repo.getAll().isEmpty())
    }

    @Test
    fun clearAll_removesAllWordPreferences() = runBlocking {
        val dao = FakeMelodyDao()
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = mapOf("静谧" to 1, "伤感" to 1), liked = true)
        repo.recordSongOutcome(votes = mapOf("燃" to 1), liked = false)
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

        /**
         * 原子累加某词条的喜欢/不喜欢票（评审 2026-10-27 · M-22 新增 DAO 方法）。
         *
         * 与真库 SQL 的 `MIN(x + delta, 2147483647)` 语义对齐：票数停在 Int 上限，
         * 不许溢出成负数（负数会让「票多者胜」判定静默翻转）。
         */
        override suspend fun ensureWordPreference(word: String, now: Long) = Unit

        override suspend fun addWordVotesDelta(
            word: String,
            likeDelta: Int,
            dislikeDelta: Int,
            now: Long,
        ) {
            val existing = words.value.firstOrNull { it.word == word }
            words.value = if (existing == null) {
                words.value + WordPreferenceEntity(
                    word = word,
                    likeVotes = likeDelta,
                    dislikeVotes = dislikeDelta,
                    updatedAt = now,
                )
            } else {
                words.value.map {
                    if (it.word != word) {
                        it
                    } else {
                        it.copy(
                            likeVotes = (it.likeVotes.toLong() + likeDelta.toLong())
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            dislikeVotes = (it.dislikeVotes.toLong() + dislikeDelta.toLong())
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                            updatedAt = now,
                        )
                    }
                }
            }
        }

        override suspend fun songs(): List<cn.com.dcsgo.mihx.data.local.entity.SongEntity> = emptyList()
        override suspend fun songIds(): List<Int> = emptyList()
        override suspend fun songAlbumIds(): List<cn.com.dcsgo.mihx.data.local.dao.SongAlbumIdRow> = emptyList()
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

        /** 原子自增短播放计数（评审 2026-10-27 · M-22 新增 DAO 方法）；本 fake 不覆盖秒切表。 */
        override suspend fun ensureQuickSkipShortPlay(songId: Int, now: Long) = Unit
        override suspend fun incrementQuickSkipShortPlay(songId: Int, now: Long) = Unit
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
        override suspend fun deletePlaybackEventsBefore(beforeMs: Long): Int = 0

        override suspend fun emotionTableStat(): cn.com.dcsgo.mihx.domain.repository.EmotionTableStat =
            cn.com.dcsgo.mihx.domain.repository.EmotionTableStat(count = 0, maxAnalyzedAt = 0L)

        override suspend fun emotionRecentAnalyzedTimestamps(limit: Int): List<Long> = emptyList()
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
        override suspend fun songIdsMissingVocalScore(): List<Int> = emptyList()
        override suspend fun songEmotionVersions(): List<cn.com.dcsgo.mihx.data.local.dao.SongEmotionVersion> = emptyList()
        override suspend fun emotionCorrectionCount(): Int = 0
        override suspend fun upsertSongEmotion(emotion: cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity) = Unit
        override suspend fun updateSongEmotionCorrection(
            songId: Int, tags: String, edited: Boolean?
        ) = Unit
        override suspend fun upsertUserOnlyCorrection(emotion: cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity) = Unit
        override suspend fun deleteSongEmotion(songId: Int) = Unit
        override suspend fun clearSongEmotionCorrection(songId: Int) = Unit
    }

    @Test
    fun recordSongOutcome_saturatesInsteadOfOverflowing() = runBlocking {
        // 票数撞到 Int 上限时**停在上限**, 不许溢出成负数 ——
        // 负数会让 kind 判定静默翻转(票多者胜变成票少者胜), 比溢出本身更糟
        val dao = FakeMelodyDao()
        dao.upsertWordPreferences(
            listOf(WordPreferenceEntity("emo", likeVotes = 0, dislikeVotes = Int.MAX_VALUE - 5, updatedAt = 1L)),
        )
        val repo = WordPreferencesRepository(dao)

        repo.recordSongOutcome(votes = mapOf("emo" to 100), liked = false)

        assertEquals(Int.MAX_VALUE, repo.getAll()["emo"]!!.dislikeVotes)
        // 归属判定没被负数带翻
        assertEquals(WordPreferenceKind.DISLIKE, repo.getAll()["emo"]!!.kind)
    }
}
