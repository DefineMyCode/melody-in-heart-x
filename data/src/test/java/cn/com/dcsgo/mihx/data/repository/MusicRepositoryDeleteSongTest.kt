package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.local.dao.AlbumArtistNameRow
import cn.com.dcsgo.mihx.data.local.dao.AlbumCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.ArtistCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.DailyDurationRow
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.dao.SongEmotionVersion
import cn.com.dcsgo.mihx.data.local.dao.SongPlayCountRow
import cn.com.dcsgo.mihx.data.local.entity.AlbumEntity
import cn.com.dcsgo.mihx.data.local.entity.ArtistEntity
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite
import cn.com.dcsgo.mihx.data.local.entity.toLite
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M-4（评审 2026-10-15）回归测试：
 *
 * 1. deleteSong 必须清理被删歌曲在 play_stats / playback_events / quick_skip_songs /
 *    quick_skip_short_play_counts / song_emotions 的全部关联行（旧实现只清情绪行）。
 * 2. 重启恢复（restoreFromRoom）后 nextId 不得复用被删歌曲的 id —— 旧实现 nextId = max(songs.id)+1，
 *    删掉最大 id 的歌后新导入歌曲会继承其遗留统计。
 * 3. 周/月 Top 榜必须先过滤孤儿行（songId 不在曲库）再 take(N)（旧实现先 take 后过滤，孤儿挤占名额）。
 * 4. M-2 数据路径：SongEmotionsRepository.observeAll 把 Room 行映射为域模型全量流。
 */
class MusicRepositoryDeleteSongTest {

    @Test
    fun deleteSongCleansStatsEventsQuickSkipAndEmotions() = runBlocking {
        val dao = fakeDaoWithAssociations()
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()
        assertTrue(dao.playStats.any { it.songId == DELETED_SONG_ID })

        val result = repository.deleteSong(DELETED_SONG_ID)
        assertTrue("删除应成功（无本地文件视为已缺失，仅移出列表）: ${result}", result is cn.com.dcsgo.mihx.domain.model.DeleteSongResult.Success)

        assertEquals(emptyList<PlayStatsEntity>(), dao.playStats.filter { it.songId == DELETED_SONG_ID })
        assertEquals(emptyList<PlaybackEventEntity>(), dao.playbackEvents.filter { it.songId == DELETED_SONG_ID })
        assertEquals(emptyList<QuickSkipSongEntity>(), dao.quickSkipSongs.filter { it.songId == DELETED_SONG_ID })
        assertEquals(emptyList<QuickSkipShortPlayEntity>(), dao.quickSkipShortPlayCounts.filter { it.songId == DELETED_SONG_ID })
        assertEquals(emptyList<SongEmotionEntity>(), dao.songEmotions.filter { it.songId == DELETED_SONG_ID })

        // 其他歌曲的统计不受影响
        assertTrue(dao.playStats.any { it.songId == KEPT_SONG_ID })
    }

    @Test
    fun nextIdDoesNotReuseDeletedSongIdAfterRestart() = runBlocking {
        // 模拟：id=3 的歌已被删除，但其统计/秒切残留仍在（旧版本删除不清理）
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
            playStats += PlayStatsEntity(songId = 3, playCount = 42, rawPlayCount = 50, totalDurationMs = 9L, lastPlayedAt = 1L)
            quickSkipSongs += QuickSkipSongEntity(songId = 3, addedAt = 1L)
        }
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()

        // 旧实现：nextId = max(songs.id)+1 = 3 → 复用被删歌曲 id，继承其 42 次播放统计
        assertEquals("nextId 应超过全库已知最大 songId（含残留关联表）", 4, repository.peekNextSongId())
    }

    @Test
    fun weeklyTopFiltersOrphanRowsBeforeTake() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
            // 孤儿事件：songId=999 不在曲库，但有效播放次数更高
            repeat(11) {
                playbackEvents += PlaybackEventEntity(songId = 999, startedAtMs = NOW - 1000, durationMs = 60_000, isEffectivePlay = true)
            }
            playbackEvents += PlaybackEventEntity(songId = 1, startedAtMs = NOW - 1000, durationMs = 60_000, isEffectivePlay = true)
            playbackEvents += PlaybackEventEntity(songId = 2, startedAtMs = NOW - 1000, durationMs = 60_000, isEffectivePlay = true)
        }
        val repository = PlayStatsRepository(dao)

        val snapshot = repository.playbackStatsSnapshot()

        assertFalse("周榜不得包含孤儿 songId", snapshot.weeklyTop.any { it.first == 999 })
        assertTrue(snapshot.weeklyTop.any { it.first == 1 })
        assertFalse("月榜不得包含孤儿 songId", snapshot.monthlyTop.any { it.first == 999 })
    }

    @Test
    fun observeAllMapsRoomRowsToDomainEmotions() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songEmotions += SongEmotionEntity(
                songId = 1,
                valence = 0.2f,
                arousal = 0.8f,
                curveJson = "[0.1,0.2]",
                peakSec = 3f,
                windowsAnalyzed = 2,
                durationSec = 10f,
                modelVersion = "v1",
                analyzedAt = 5L,
                embeddingB64 = null,
                userValence = 0.3f,
                userArousal = 0.7f,
                userTags = "开心,平静",
            )
        }
        val repository = SongEmotionsRepository(dao)

        val all = repository.observeAll().first()

        assertTrue(1 in all)
        val emotion = all.getValue(1)
        assertEquals("v1", emotion.modelVersion)
        assertEquals(listOf("开心", "平静"), emotion.userTags)
        assertEquals(0.3f, emotion.userValence)
    }

    private companion object {
        const val DELETED_SONG_ID = 2
        const val KEPT_SONG_ID = 1
        val NOW: Long = System.currentTimeMillis()
    }

    private fun fakeDaoWithAssociations(): FakeMelodyDao = FakeMelodyDao().apply {
        songs += listOf(songEntity(KEPT_SONG_ID), songEntity(DELETED_SONG_ID))
        playStats += PlayStatsEntity(songId = KEPT_SONG_ID, playCount = 3, rawPlayCount = 4, totalDurationMs = 111L, lastPlayedAt = 1L)
        playStats += PlayStatsEntity(songId = DELETED_SONG_ID, playCount = 5, rawPlayCount = 7, totalDurationMs = 999L, lastPlayedAt = 1L)
        playbackEvents += PlaybackEventEntity(songId = DELETED_SONG_ID, startedAtMs = NOW - 2000, durationMs = 30_000, isEffectivePlay = true)
        quickSkipSongs += QuickSkipSongEntity(songId = DELETED_SONG_ID, addedAt = 1L)
        quickSkipShortPlayCounts += QuickSkipShortPlayEntity(songId = DELETED_SONG_ID, count = 2, updatedAt = 1L)
        songEmotions += SongEmotionEntity(
            songId = DELETED_SONG_ID,
            valence = 0.5f,
            arousal = 0.5f,
            curveJson = "[]",
            peakSec = 0f,
            windowsAnalyzed = 1,
            durationSec = 1f,
            modelVersion = "v1",
            analyzedAt = 1L,
            embeddingB64 = null,
            userValence = null,
            userArousal = null,
            userTags = null,
        )
    }

    private fun songEntity(id: Int): SongEntity = SongEntity(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album $id",
        sampleRate = 44_100,
        uri = null,
        displayName = "song-$id.mp3",
        mimeType = "audio/mpeg",
        lastModified = null,
        size = null,
        sourceTreeUri = null,
        albumArtCacheUri = null,
        lrcUri = null,
        importedAt = 1L,
    )

    private class FakeMelodyDao : MelodyDao {
        val songs = mutableListOf<SongEntity>()
        val playlists = mutableListOf<PlaylistEntity>()
        val playlistSongRefs = mutableListOf<PlaylistSongCrossRef>()
        val songGroupOverrides = mutableListOf<SongGroupOverrideEntity>()
        val playStats = mutableListOf<PlayStatsEntity>()
        val playbackEvents = mutableListOf<PlaybackEventEntity>()
        val quickSkipSongs = mutableListOf<QuickSkipSongEntity>()
        val quickSkipShortPlayCounts = mutableListOf<QuickSkipShortPlayEntity>()
        val migrationStates = mutableListOf<MigrationStateEntity>()
        val artists = mutableListOf<ArtistEntity>()
        val albums = mutableListOf<AlbumEntity>()
        val songArtistRefs = mutableListOf<SongArtistCrossRef>()
        val songEmotions = mutableListOf<SongEmotionEntity>()

        override suspend fun songs(): List<SongEntity> = songs.sortedBy { it.id }
        override suspend fun songCount(): Int = songs.size
        override suspend fun playlists(): List<PlaylistEntity> = playlists.sortedBy { it.id }
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> =
            playlistSongRefs.sortedWith(compareBy({ it.playlistId }, { it.sortOrder }))
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats.sortedBy { it.songId }
        override suspend fun playStat(songId: Int): PlayStatsEntity? = playStats.firstOrNull { it.songId == songId }
        override suspend fun playStatsIn(songIds: List<Int>): List<PlayStatsEntity> =
            playStats.filter { it.songId in songIds }
        override suspend fun insertPlaybackEvent(event: PlaybackEventEntity) {
            playbackEvents += event
        }
        override suspend fun totalDurationBetween(startMs: Long, endMs: Long): Long =
            playbackEvents.filter { it.startedAtMs in startMs until endMs }.sumOf { it.durationMs }
        override suspend fun distinctSongsBetween(startMs: Long, endMs: Long): Int =
            playbackEvents.filter { it.startedAtMs in startMs until endMs }.map { it.songId }.distinct().size
        override suspend fun dailyDurationsBetween(startMs: Long, endMs: Long): List<DailyDurationRow> = emptyList()
        override suspend fun playCountsBetween(startMs: Long, endMs: Long): List<SongPlayCountRow> =
            playbackEvents
                .filter { it.startedAtMs in startMs until endMs && it.isEffectivePlay }
                .groupBy { it.songId }
                .map { (songId, events) -> SongPlayCountRow(songId, events.size) }
                .sortedWith(compareByDescending<SongPlayCountRow> { it.playCount }.thenBy { it.songId })
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
        override suspend fun upsertSongs(newSongs: List<SongEntity>) = upsertAll(songs, newSongs) { it.id }
        override suspend fun upsertPlaylists(values: List<PlaylistEntity>) = upsertAll(playlists, values) { it.id }
        override suspend fun upsertPlaylistSongs(refs: List<PlaylistSongCrossRef>) =
            upsertAll(playlistSongRefs, refs) { it.playlistId to it.songId }
        override suspend fun upsertPlayStats(values: List<PlayStatsEntity>) = upsertAll(playStats, values) { it.songId }
        override suspend fun upsertPlayStat(stat: PlayStatsEntity) = upsertAll(playStats, listOf(stat)) { it.songId }
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
        override suspend fun upsertQuickSkipSongs(values: List<QuickSkipSongEntity>) = upsertAll(quickSkipSongs, values) { it.songId }
        override suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity) = upsertAll(quickSkipSongs, listOf(song)) { it.songId }
        override suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity) =
            upsertAll(quickSkipShortPlayCounts, listOf(count)) { it.songId }
        override suspend fun upsertSongGroupOverrides(overrides: List<SongGroupOverrideEntity>) =
            upsertAll(songGroupOverrides, overrides) { it.songId }
        override suspend fun insertArtists(values: List<ArtistEntity>) { artists += values }
        override suspend fun insertAlbums(values: List<AlbumEntity>) { albums += values }
        override suspend fun insertSongArtistRefs(refs: List<SongArtistCrossRef>) { songArtistRefs += refs }
        override suspend fun deleteAllSongs() = songs.clear()
        override suspend fun deleteAllSongGroupOverrides() = songGroupOverrides.clear()
        override suspend fun deleteAllPlaylists() = playlists.clear()
        override suspend fun deleteAllPlaylistSongRefs() = playlistSongRefs.clear()
        override suspend fun deleteQuickSkipSong(songId: Int) { quickSkipSongs.removeIf { it.songId == songId } }
        override suspend fun deleteQuickSkipShortPlay(songId: Int) { quickSkipShortPlayCounts.removeIf { it.songId == songId } }
        override suspend fun deletePlayStatsForSongs(songIds: List<Int>) { playStats.removeIf { it.songId in songIds } }
        override suspend fun deletePlaybackEventsForSongs(songIds: List<Int>) { playbackEvents.removeIf { it.songId in songIds } }
        override suspend fun deleteQuickSkipSongsFor(songIds: List<Int>) { quickSkipSongs.removeIf { it.songId in songIds } }
        override suspend fun deleteQuickSkipShortPlaysFor(songIds: List<Int>) { quickSkipShortPlayCounts.removeIf { it.songId in songIds } }
        override suspend fun deleteSongEmotionsFor(songIds: List<Int>) { songEmotions.removeIf { it.songId in songIds } }
        override suspend fun deleteAllSongArtistRefs() = songArtistRefs.clear()
        override suspend fun deleteAllArtists() = artists.clear()
        override suspend fun deleteAllAlbums() = albums.clear()
        override suspend fun deleteOrphanArtists() {
            val used = songArtistRefs.map { it.artistId }.toSet()
            artists.removeIf { it.artistId !in used }
        }
        override suspend fun deleteOrphanAlbums() {
            val used = songs.mapNotNull { it.albumId }.toSet()
            albums.removeIf { it.albumId !in used }
        }
        override suspend fun migrationState(name: String): MigrationStateEntity? =
            migrationStates.firstOrNull { it.name == name }
        override suspend fun upsertMigrationState(state: MigrationStateEntity) =
            upsertAll(migrationStates, listOf(state)) { it.name }
        override suspend fun songEmotion(songId: Int): SongEmotionEntity? = songEmotions.firstOrNull { it.songId == songId }
        override suspend fun allSongEmotions(): List<SongEmotionLite> = songEmotions.map { it.toLite() }
        override fun observeAllSongEmotions(): Flow<List<SongEmotionLite>> =
            flow { emit(songEmotions.map { it.toLite() }) }
        override suspend fun maxKnownSongId(): Int? = listOf(
            songs.maxOfOrNull { it.id },
            playStats.maxOfOrNull { it.songId },
            playbackEvents.maxOfOrNull { it.songId },
            quickSkipSongs.maxOfOrNull { it.songId },
            quickSkipShortPlayCounts.maxOfOrNull { it.songId },
            songEmotions.maxOfOrNull { it.songId },
        ).filterNotNull().maxOrNull()
        override suspend fun songEmotionVersions(): List<SongEmotionVersion> =
            songEmotions.map { SongEmotionVersion(it.songId, it.modelVersion) }
        override suspend fun emotionAnalyzedTimeline(): List<Long> = emptyList()
        override suspend fun emotionCorrectionCount(): Int = songEmotions.count { !it.userTags.isNullOrEmpty() }
        override suspend fun upsertSongEmotion(emotion: SongEmotionEntity) {
            songEmotions.removeAll { it.songId == emotion.songId }
            songEmotions.add(emotion)
        }
        override suspend fun updateSongEmotionCorrection(
            songId: Int, tags: String, edited: Boolean?
        ) {
            val idx = songEmotions.indexOfFirst { it.songId == songId }
            if (idx >= 0) songEmotions[idx] = songEmotions[idx].copy(userValence = null, userArousal = null, userTags = tags, userTagsEdited = edited)
        }
        override suspend fun upsertUserOnlyCorrection(emotion: SongEmotionEntity) {
            songEmotions.removeAll { it.songId == emotion.songId }
            songEmotions.add(emotion)
        }
        override suspend fun deleteSongEmotion(songId: Int) {
            songEmotions.removeAll { it.songId == songId }
        }
        override suspend fun clearSongEmotionCorrection(songId: Int) {
            val idx = songEmotions.indexOfFirst { it.songId == songId }
            if (idx >= 0) songEmotions[idx] = songEmotions[idx].copy(
                userValence = null, userArousal = null, userTags = null,
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

        private fun <T, K> upsertAll(target: MutableList<T>, values: List<T>, key: (T) -> K) {
            values.forEach { value ->
                val index = target.indexOfFirst { key(it) == key(value) }
                if (index >= 0) target[index] = value else target.add(value)
            }
        }
    }
}
