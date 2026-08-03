package cn.com.dcsgo.mihx.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity

@Dao
interface MelodyDao {
    @Query("SELECT * FROM songs ORDER BY id")
    suspend fun songs(): List<SongEntity>

    @Query("SELECT * FROM playlists ORDER BY id")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_song_cross_ref ORDER BY playlistId, sortOrder")
    suspend fun playlistSongRefs(): List<PlaylistSongCrossRef>

    @Query("SELECT * FROM song_group_overrides")
    suspend fun songGroupOverrides(): List<SongGroupOverrideEntity>

    @Query("SELECT * FROM play_stats ORDER BY songId")
    suspend fun playStats(): List<PlayStatsEntity>

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    suspend fun playStat(songId: Int): PlayStatsEntity?

    @Query("SELECT * FROM quick_skip_songs ORDER BY songId")
    suspend fun quickSkipSongs(): List<QuickSkipSongEntity>

    @Query("SELECT * FROM quick_skip_songs WHERE songId = :songId")
    suspend fun quickSkipSong(songId: Int): QuickSkipSongEntity?

    @Query("SELECT * FROM quick_skip_short_play_counts WHERE songId = :songId")
    suspend fun quickSkipShortPlay(songId: Int): QuickSkipShortPlayEntity?

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Upsert
    suspend fun upsertPlaylists(playlists: List<PlaylistEntity>)

    @Upsert
    suspend fun upsertPlaylistSongs(refs: List<PlaylistSongCrossRef>)

    @Upsert
    suspend fun upsertPlayStats(stats: List<PlayStatsEntity>)

    @Upsert
    suspend fun upsertPlayStat(stat: PlayStatsEntity)

    @Upsert
    suspend fun upsertQuickSkipSongs(songs: List<QuickSkipSongEntity>)

    @Upsert
    suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity)

    @Upsert
    suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity)

    @Upsert
    suspend fun upsertSongGroupOverrides(overrides: List<SongGroupOverrideEntity>)

    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()

    @Query("DELETE FROM song_group_overrides")
    suspend fun deleteAllSongGroupOverrides()

    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    @Query("DELETE FROM playlist_song_cross_ref")
    suspend fun deleteAllPlaylistSongRefs()

    @Query("DELETE FROM quick_skip_songs WHERE songId = :songId")
    suspend fun deleteQuickSkipSong(songId: Int)

    @Query("DELETE FROM quick_skip_short_play_counts WHERE songId = :songId")
    suspend fun deleteQuickSkipShortPlay(songId: Int)

    @Transaction
    suspend fun replaceSongs(
        songs: List<SongEntity>,
        overrides: List<SongGroupOverrideEntity>,
    ) {
        deleteAllSongGroupOverrides()
        deleteAllSongs()
        if (songs.isNotEmpty()) upsertSongs(songs)
        if (overrides.isNotEmpty()) upsertSongGroupOverrides(overrides)
    }

    @Transaction
    suspend fun replacePlaylists(
        playlists: List<PlaylistEntity>,
        refs: List<PlaylistSongCrossRef>,
    ) {
        deleteAllPlaylistSongRefs()
        deleteAllPlaylists()
        if (playlists.isNotEmpty()) upsertPlaylists(playlists)
        if (refs.isNotEmpty()) upsertPlaylistSongs(refs)
    }

    @Query("SELECT * FROM migration_state WHERE name = :name")
    suspend fun migrationState(name: String): MigrationStateEntity?

    @Upsert
    suspend fun upsertMigrationState(state: MigrationStateEntity)
}
