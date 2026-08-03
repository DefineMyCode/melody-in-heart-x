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
    fun updateSongTitleOverridePersistsAndClearsRoomOverride() = runBlocking {
        val dao = FakeMelodyDao().apply {
            songs += listOf(songEntity(1))
        }
        val repository = MusicRepository(melodyDao = dao)
        repository.loadSongs()

        assertTrue(repository.updateSongTitleOverride(songId = 1, titleOverride = "Album Version"))

        assertEquals("Album Version", repository.getSongs().single().titleOverride)
        assertEquals(
            listOf(SongGroupOverrideEntity(songId = 1, titleOverride = "Album Version", updatedAt = dao.songGroupOverrides.single().updatedAt)),
            dao.songGroupOverrides,
        )

        assertTrue(repository.updateSongTitleOverride(songId = 1, titleOverride = null))

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

        override suspend fun songs(): List<SongEntity> = songs.sortedBy { it.id }
        override suspend fun playlists(): List<PlaylistEntity> = playlists.sortedBy { it.id }
        override suspend fun playlistSongRefs(): List<PlaylistSongCrossRef> =
            playlistSongRefs.sortedWith(compareBy({ it.playlistId }, { it.sortOrder }))
        override suspend fun songGroupOverrides(): List<SongGroupOverrideEntity> = songGroupOverrides
        override suspend fun playStats(): List<PlayStatsEntity> = playStats.sortedBy { it.songId }
        override suspend fun playStat(songId: Int): PlayStatsEntity? = playStats.firstOrNull { it.songId == songId }
        override suspend fun quickSkipSongs(): List<QuickSkipSongEntity> = quickSkipSongs.sortedBy { it.songId }
        override suspend fun quickSkipSong(songId: Int): QuickSkipSongEntity? =
            quickSkipSongs.firstOrNull { it.songId == songId }
        override suspend fun quickSkipShortPlay(songId: Int): QuickSkipShortPlayEntity? =
            quickSkipShortPlayCounts.firstOrNull { it.songId == songId }

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
            migrationStates.upsertBy(listOf(state)) { it.name }
        }
    }

    private fun songEntity(id: Int): SongEntity {
        return SongEntity(
            id = id,
            title = "Song $id",
            artist = "Artist",
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
