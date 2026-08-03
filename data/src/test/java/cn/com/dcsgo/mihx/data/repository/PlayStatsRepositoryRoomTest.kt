package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlayStatsRepositoryRoomTest {

    @Test
    fun incrementsAndDurationUpdatesPersistToRoom() {
        val dao = FakeMelodyDao().apply {
            playStats += PlayStatsEntity(
                songId = 7,
                playCount = 2,
                rawPlayCount = 3,
                totalDurationMs = 1_000L,
                lastPlayedAt = null,
            )
        }
        val repository = PlayStatsRepository(melodyDao = dao)

        assertEquals(3, repository.increment(7))
        assertEquals(4, repository.incrementRawPlayCount(7))
        assertEquals(1_500L, repository.updatePlayDuration(7, 500L))

        val stat = dao.playStats.single()
        assertEquals(PlayStatsEntity(7, playCount = 3, rawPlayCount = 4, totalDurationMs = 1_500L, lastPlayedAt = stat.lastPlayedAt), stat)
        assertNotNull(stat.lastPlayedAt)
    }

    @Test
    fun batchReadsUseRoomStatsAndDefaultMissingSongsToZero() {
        val dao = FakeMelodyDao().apply {
            playStats += PlayStatsEntity(
                songId = 2,
                playCount = 5,
                rawPlayCount = 8,
                totalDurationMs = 12_000L,
                lastPlayedAt = 1L,
            )
        }
        val repository = PlayStatsRepository(melodyDao = dao)

        assertEquals(mapOf(2 to 5, 3 to 0), repository.getCounts(listOf(2, 3)))
        assertEquals(mapOf(2 to 8, 3 to 0), repository.getRawPlayCounts(listOf(2, 3)))
        assertEquals(mapOf(2 to 5), repository.getAllCounts())
    }

    @Test
    fun rankedCountsSortsRoomStatsDeterministically() {
        val dao = FakeMelodyDao().apply {
            playStats += listOf(
                PlayStatsEntity(songId = 3, playCount = 7, rawPlayCount = 1, totalDurationMs = 0L, lastPlayedAt = null),
                PlayStatsEntity(songId = 1, playCount = 7, rawPlayCount = 9, totalDurationMs = 0L, lastPlayedAt = null),
                PlayStatsEntity(songId = 2, playCount = 1, rawPlayCount = 9, totalDurationMs = 0L, lastPlayedAt = null),
                PlayStatsEntity(songId = 4, playCount = 0, rawPlayCount = 0, totalDurationMs = 0L, lastPlayedAt = null),
            )
        }
        val repository = PlayStatsRepository(melodyDao = dao)

        assertEquals(
            listOf(1 to 7, 3 to 7, 2 to 1),
            repository.getRankedCounts(useRawCounts = false, descending = true),
        )
        assertEquals(
            listOf(2 to 1, 1 to 7, 3 to 7),
            repository.getRankedCounts(useRawCounts = false, descending = false),
        )
        assertEquals(
            listOf(1 to 9, 2 to 9, 3 to 1),
            repository.getRankedCounts(useRawCounts = true, descending = true),
        )
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

        override suspend fun songs(): List<SongEntity> = songs
        override suspend fun playlists(): List<PlaylistEntity> = playlists
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> = playlistSongRefs
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats.sortedBy { it.songId }
        override suspend fun playStat(songId: Int): PlayStatsEntity? = playStats.firstOrNull { it.songId == songId }
        override suspend fun quickSkipSongs(): List<QuickSkipSongEntity> = quickSkipSongs
        override suspend fun quickSkipSong(songId: Int): QuickSkipSongEntity? =
            quickSkipSongs.firstOrNull { it.songId == songId }
        override suspend fun quickSkipShortPlay(songId: Int): QuickSkipShortPlayEntity? =
            quickSkipShortPlayCounts.firstOrNull { it.songId == songId }

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

        override suspend fun upsertQuickSkipSongs(songs: List<QuickSkipSongEntity>) {
            quickSkipSongs.upsertItems(songs) { it.songId }
        }

        override suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity) {
            quickSkipSongs.upsertItems(listOf(song)) { it.songId }
        }

        override suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity) {
            quickSkipShortPlayCounts.upsertItems(listOf(count)) { it.songId }
        }

        override suspend fun upsertSongGroupOverrides(overrides: List<SongGroupOverrideEntity>) {
            songGroupOverrides.upsertItems(overrides) { it.songId }
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

        override suspend fun migrationState(name: String): MigrationStateEntity? =
            migrationStates.firstOrNull { it.name == name }

        override suspend fun upsertMigrationState(state: MigrationStateEntity) {
            migrationStates.upsertItems(listOf(state)) { it.name }
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
