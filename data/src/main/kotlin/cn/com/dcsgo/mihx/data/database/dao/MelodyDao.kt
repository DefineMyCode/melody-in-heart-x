package cn.com.dcsgo.mihx.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.com.dcsgo.mihx.data.database.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.database.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.database.entity.PlaylistSongCrossRefEntity
import cn.com.dcsgo.mihx.data.database.entity.ShortPlayCountEntity
import cn.com.dcsgo.mihx.data.database.entity.SkipSongEntity
import cn.com.dcsgo.mihx.data.database.entity.SongEntity
import cn.com.dcsgo.mihx.data.database.entity.SongGroupOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * Single aggregate DAO (Phase 4 evolves per aggregate root).
 *
 * One-row tables (play_stats / skip_songs / short_play_counts / song_group_overrides /
 * migration_state) use the entity's primary key, so upsert == Insert(REPLACE).
 */
@Dao
interface MelodyDao {
    // ---- Songs ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity): Long

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongsByIds(ids: List<Long>): List<SongEntity>

    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE uri = :uri LIMIT 1")
    suspend fun getSongByUri(uri: String): SongEntity?

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteSong(id: Long)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    // ---- Playlists ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC, id DESC")
    suspend fun getPlaylists(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistSong(ref: PlaylistSongCrossRefEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongCrossRefEntity>

    // ---- Play stats ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayStats(stats: PlayStatsEntity)

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    suspend fun getPlayStats(songId: Long): PlayStatsEntity?

    // ---- Skip tracking ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSkipSong(entity: SkipSongEntity)

    @Query("SELECT * FROM skip_songs WHERE songId = :songId")
    suspend fun getSkipSong(songId: Long): SkipSongEntity?

    // ---- Short-play tracking ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShortPlayCount(entity: ShortPlayCountEntity)

    @Query("SELECT * FROM short_play_counts WHERE songId = :songId")
    suspend fun getShortPlayCount(songId: Long): ShortPlayCountEntity?

    // ---- Same-title group overrides ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGroupOverride(entity: SongGroupOverrideEntity)

    @Query("SELECT * FROM song_group_overrides WHERE groupKey = :groupKey")
    suspend fun getGroupOverride(groupKey: String): SongGroupOverrideEntity?

    // ---- Migration scratch space ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrationState(entity: MigrationStateEntity)

    @Query("SELECT * FROM migration_state WHERE `key` = :key")
    suspend fun getMigrationState(key: String): MigrationStateEntity?
}
