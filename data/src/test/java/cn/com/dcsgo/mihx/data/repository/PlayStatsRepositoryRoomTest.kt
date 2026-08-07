package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.data.local.dao.AlbumArtistNameRow
import cn.com.dcsgo.mihx.data.local.dao.AlbumCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.ArtistCatalogRow
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
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
        val artists = mutableListOf<ArtistEntity>()
        val albums = mutableListOf<AlbumEntity>()
        val songArtistRefs = mutableListOf<SongArtistCrossRef>()

        override suspend fun songs(): List<SongEntity> = songs
        override suspend fun playlists(): List<PlaylistEntity> = playlists
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> = playlistSongRefs
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats.sortedBy { it.songId }
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
