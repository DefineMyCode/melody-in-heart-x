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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickSkipSongsRepositoryRoomTest {

    @Test
    fun loadsInitialRoomSongIdsAndAnswersMembership() {
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
    fun addAndRemovePersistToRoomAndRejectDuplicates() {
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
    fun shortPlayCountsPersistToRoomAndReset() {
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

        override suspend fun songs(): List<SongEntity> = songs
        override suspend fun playlists(): List<PlaylistEntity> = playlists
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> = playlistSongRefs
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats
        override suspend fun playStat(songId: Int): PlayStatsEntity? = playStats.firstOrNull { it.songId == songId }
        override suspend fun quickSkipSongs(): List<QuickSkipSongEntity> = quickSkipSongs.sortedBy { it.songId }
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
