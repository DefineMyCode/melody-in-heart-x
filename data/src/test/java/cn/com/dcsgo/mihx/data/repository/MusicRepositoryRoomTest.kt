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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryRoomTest {

    @Test
    fun loadSongsRestoresPlaylistCrossRefsFromRoom() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
            playlists += PlaylistEntity(id = 10, name = "Favorites", createdAt = 1L, updatedAt = 1L)
            playlistSongRefs += listOf(
                PlaylistSongCrossRef(playlistId = 10, songId = 2, sortOrder = 0),
                PlaylistSongCrossRef(playlistId = 10, songId = 1, sortOrder = 1),
            )
        }
        val repository = MusicRepository(melodyDao = dao)

        repository.loadSongs()

        assertEquals(listOf(1, 2), repository.getSongs().map { it.id })
        assertEquals(listOf(2, 1), repository.getSongsByPlaylistId(10).map { it.id })
        assertEquals(2, repository.getPlaylists().single().songCount)
    }

    @Test
    fun loadSongsFiltersOrphanPlaylistCrossRefsFromRoom() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(3))
            playlists += PlaylistEntity(id = 10, name = "Favorites", createdAt = 1L, updatedAt = 1L)
            playlistSongRefs += listOf(
                PlaylistSongCrossRef(playlistId = 10, songId = 1, sortOrder = 0),
                PlaylistSongCrossRef(playlistId = 10, songId = 2, sortOrder = 1),
                PlaylistSongCrossRef(playlistId = 10, songId = 3, sortOrder = 2),
            )
        }
        val repository = MusicRepository(melodyDao = dao)

        repository.loadSongs()

        assertEquals(listOf(1, 3), repository.getSongsByPlaylistId(10).map { it.id })
        assertEquals(2, repository.getPlaylists().single().songCount)
    }

    @Test
    fun addSongToPlaylistPersistsCrossRefsAndRejectsDuplicates() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
        }
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()
        val playlist = repository.createPlaylist("Favorites")

        assertTrue(repository.addSongToPlaylist(playlist.id, 1))
        assertFalse(repository.addSongToPlaylist(playlist.id, 1))
        assertTrue(repository.addSongToPlaylist(playlist.id, 2))
        repository.flushPersists()

        assertEquals(listOf(1, 2), repository.getSongsByPlaylistId(playlist.id).map { it.id })
        assertEquals(
            listOf(
                PlaylistSongCrossRef(playlistId = playlist.id, songId = 1, sortOrder = 0),
                PlaylistSongCrossRef(playlistId = playlist.id, songId = 2, sortOrder = 1),
            ),
            dao.playlistSongRefs,
        )
    }

    @Test
    fun validateAndCleanupLocalFilesKeepsAllWhenNoContext() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
        }
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()

        val result = repository.validateAndCleanupLocalFiles()

        assertEquals(2, result.totalSongs)
        assertEquals(0, result.missingCount)
        assertEquals(0, result.removedPlaylistRefs)
        assertTrue(result.removedSongIds.isEmpty())
        assertEquals(listOf(1, 2), repository.getSongs().map { it.id })
    }

    @Test
    fun updateSongTitleOverridePersistsAndClearsRoomOverride() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1))
        }
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()

        assertTrue(repository.updateSongTitleOverride(songId = 1, titleOverride = "Album Version"))
        repository.flushPersists()

        assertEquals("Album Version", repository.getSongs().single().titleOverride)
        assertEquals(
            listOf(SongGroupOverrideEntity(songId = 1, titleOverride = "Album Version", updatedAt = dao.songGroupOverrides.single().updatedAt)),
            dao.songGroupOverrides,
        )

        assertTrue(repository.updateSongTitleOverride(songId = 1, titleOverride = null))
        repository.flushPersists()

        assertEquals(null, repository.getSongs().single().titleOverride)
        assertEquals(emptyList<SongGroupOverrideEntity>(), dao.songGroupOverrides)
    }

    @Test
    fun loadSongsRestoresSongTitleOverridesFromRoom() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1), songEntity(2))
            songGroupOverrides += SongGroupOverrideEntity(
                songId = 2,
                titleOverride = "Concert Version",
                updatedAt = 100L,
            )
        }
        val repository = MusicRepository(melodyDao = dao)

        repository.loadSongs()

        val songsById = repository.getSongs().associateBy { it.id }
        assertEquals(null, songsById.getValue(1).titleOverride)
        assertEquals("Concert Version", songsById.getValue(2).titleOverride)
        assertEquals("Concert Version", songsById.getValue(2).groupKey)
        assertEquals("Album 1", songsById.getValue(1).album)
    }

    @Test
    fun loadSongsSyncsArtistAndAlbumCatalog() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(
                SongEntity(
                    id = 1, title = "Song 1", artist = "A / B", album = "Album X",
                    sampleRate = 44_100, uri = null, displayName = null, mimeType = null,
                    lastModified = null, size = null, sourceTreeUri = null,
                    albumArtCacheUri = null, lrcUri = null, importedAt = 1L,
                ),
                SongEntity(
                    id = 2, title = "Song 2", artist = "B", album = "Album X",
                    sampleRate = 44_100, uri = null, displayName = null, mimeType = null,
                    lastModified = null, size = null, sourceTreeUri = null,
                    albumArtCacheUri = null, lrcUri = null, importedAt = 1L,
                ),
                SongEntity(
                    id = 3, title = "Song 3", artist = "C", album = "Album Y",
                    sampleRate = 44_100, uri = null, displayName = null, mimeType = null,
                    lastModified = null, size = null, sourceTreeUri = null,
                    albumArtCacheUri = null, lrcUri = null, importedAt = 1L,
                ),
            )
        }
        val repository = MusicRepository(melodyDao = dao)

        repository.loadSongs()

        // 歌手目录：A、B、C 三个原子歌手
        assertEquals(setOf("A", "B", "C"), dao.artists.map { it.name }.toSet())
        // 专辑目录：Album X、Album Y
        assertEquals(setOf("Album X", "Album Y"), dao.albums.map { it.name }.toSet())
        // 多对多关联：Song 1 → A,B；Song 2 → B；Song 3 → C
        val refsBySong = dao.songArtistRefs.groupBy { it.songId }
            .mapValues { (_, refs) -> refs.map { ref -> dao.artists.first { it.artistId == ref.artistId }.name }.toSet() }
        assertEquals(setOf("A", "B"), refsBySong[1])
        assertEquals(setOf("B"), refsBySong[2])
        assertEquals(setOf("C"), refsBySong[3])
        // 内存歌曲带上 artistIds 与 albumId
        val songsById = repository.getSongs().associateBy { it.id }
        assertEquals(setOf("A", "B"), songsById.getValue(1).artistIds.map { id -> dao.artists.first { it.artistId == id }.name }.toSet())
        assertEquals(dao.albums.first { it.name == "Album X" }.albumId, songsById.getValue(1).albumId)
        assertEquals(dao.albums.first { it.name == "Album Y" }.albumId, songsById.getValue(3).albumId)
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

        override suspend fun songs(): List<SongEntity> = songs.sortedBy { it.id }
        override suspend fun playlists(): List<PlaylistEntity> = playlists.sortedBy { it.id }
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> =
            playlistSongRefs.sortedWith(compareBy({ it.playlistId }, { it.sortOrder }))
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

        override suspend fun artistCatalog(): List<ArtistCatalogRow> = artists.map { artist ->
            val songIds = songArtistRefs.filter { it.artistId == artist.artistId }.map { it.songId }.toSet()
            ArtistCatalogRow(
                artistId = artist.artistId,
                name = artist.name,
                songCount = songIds.size,
                albumCount = songs.filter { it.id in songIds }.mapNotNull { it.albumId }.distinct().size,
                coverUri = songs.firstOrNull { it.id in songIds }?.albumArtCacheUri,
            )
        }

        override suspend fun albumCatalog(): List<AlbumCatalogRow> = albums.map { album ->
            val albumSongs = songs.filter { it.albumId == album.albumId }
            AlbumCatalogRow(
                albumId = album.albumId,
                name = album.name,
                songCount = albumSongs.size,
                coverUri = albumSongs.firstNotNullOfOrNull { it.albumArtCacheUri },
            )
        }

        override suspend fun albumArtistNames(): List<AlbumArtistNameRow> =
            songs.filter { it.albumId != null }.flatMap { song ->
                val artistNames = songArtistRefs
                    .filter { it.songId == song.id }
                    .mapNotNull { ref -> artists.firstOrNull { it.artistId == ref.artistId }?.name }
                artistNames.map { name -> AlbumArtistNameRow(song.albumId!!, name) }
            }.distinct()

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
                val existing = this.artists.firstOrNull { it.name == artist.name }
                if (existing != null) {
                    this.artists[this.artists.indexOf(existing)] = existing
                } else {
                    this.artists += artist.copy(artistId = this.artists.size + 1)
                }
            }
        }

        override suspend fun insertAlbums(albums: List<AlbumEntity>) {
            albums.forEach { album ->
                val existing = this.albums.firstOrNull { it.name == album.name }
                if (existing != null) {
                    this.albums[this.albums.indexOf(existing)] = existing
                } else {
                    this.albums += album.copy(albumId = this.albums.size + 1)
                }
            }
        }

        override suspend fun insertSongArtistRefs(refs: List<SongArtistCrossRef>) {
            refs.forEach { ref ->
                val key = ref.songId to ref.artistId
                val existing = songArtistRefs.firstOrNull { it.songId == ref.songId && it.artistId == ref.artistId }
                if (existing == null) songArtistRefs += ref
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
            migrationStates.upsertBy(listOf(state)) { it.name }
        }
    }

    private fun songEntity(id: Int): SongEntity {
        return SongEntity(
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
    }
}

private fun <T, K> MutableList<T>.upsertBy(
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
