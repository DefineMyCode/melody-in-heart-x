package cn.com.dcsgo.mihx.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.com.dcsgo.mihx.data.database.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.database.entity.PlayCountRow
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsView
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

    @Query("UPDATE songs SET albumArtUri = :uri WHERE id = :songId")
    suspend fun updateAlbumArtUri(songId: Long, uri: String)

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

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC, id DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistSong(ref: PlaylistSongCrossRefEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistSongs(refs: List<PlaylistSongCrossRefEntity>)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongs(playlistId: Long): List<PlaylistSongCrossRefEntity>

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun deletePlaylistSong(playlistId: Long, songId: Long)

    /** Ordered song entities of a playlist (joined through the cross-reference table). */
    @Query(
        """
        SELECT s.* FROM playlist_songs ps
        INNER JOIN songs s ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    suspend fun getPlaylistSongEntities(playlistId: Long): List<SongEntity>

    // ---- Play stats ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayStats(stats: PlayStatsEntity)

    @Query("SELECT * FROM play_stats WHERE songId = :songId")
    suspend fun getPlayStats(songId: Long): PlayStatsEntity?

    @Query("SELECT songId, playCount FROM play_stats")
    suspend fun getPlayCounts(): List<PlayCountRow>

    @Query("SELECT IFNULL(SUM(totalPlayedMs), 0) FROM play_stats")
    suspend fun getTotalPlayedMs(): Long

    /** Play stats joined with skip / short-play counters (P5-C stats screen + skip list). */
    @Query(
        """
        SELECT p.songId AS songId,
               p.playCount AS playCount,
               p.totalPlayedMs AS totalPlayedMs,
               p.lastPlayedAt AS lastPlayedAt,
               IFNULL(sk.skipCount, 0) AS skipCount,
               IFNULL(sp.shortPlayCount, 0) AS shortPlayCount
        FROM play_stats p
        LEFT JOIN skip_songs sk ON sk.songId = p.songId
        LEFT JOIN short_play_counts sp ON sp.songId = p.songId
        """,
    )
    suspend fun getPlayStatsList(): List<PlayStatsView>

    @Query(
        """
        SELECT p.songId AS songId,
               p.playCount AS playCount,
               p.totalPlayedMs AS totalPlayedMs,
               p.lastPlayedAt AS lastPlayedAt,
               IFNULL(sk.skipCount, 0) AS skipCount,
               IFNULL(sp.shortPlayCount, 0) AS shortPlayCount
        FROM play_stats p
        LEFT JOIN skip_songs sk ON sk.songId = p.songId
        LEFT JOIN short_play_counts sp ON sp.songId = p.songId
        """,
    )
    fun observePlayStatsList(): Flow<List<PlayStatsView>>

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

    @Query("SELECT * FROM song_group_overrides")
    suspend fun getAllGroupOverrides(): List<SongGroupOverrideEntity>

    @Query("SELECT * FROM song_group_overrides")
    fun observeGroupOverrides(): Flow<List<SongGroupOverrideEntity>>

    @Query("DELETE FROM song_group_overrides WHERE groupKey = :groupKey")
    suspend fun deleteGroupOverride(groupKey: String)

    // ---- Migration scratch space ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrationState(entity: MigrationStateEntity)

    @Query("SELECT * FROM migration_state WHERE `key` = :key")
    suspend fun getMigrationState(key: String): MigrationStateEntity?
}
