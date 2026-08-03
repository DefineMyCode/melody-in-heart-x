package cn.com.dcsgo.mihx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cn.com.dcsgo.mihx.core.model.Song
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

/** 歌手目录聚合行 */
data class ArtistCatalogRow(
    val artistId: Int,
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val coverUri: String?,
)

/** 专辑目录聚合行 */
data class AlbumCatalogRow(
    val albumId: Int,
    val name: String,
    val songCount: Int,
    val coverUri: String?,
)

/** 专辑-歌手名称映射行 */
data class AlbumArtistNameRow(
    val albumId: Int,
    val artistName: String,
)

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

    @Query("SELECT * FROM artists ORDER BY name")
    suspend fun artists(): List<ArtistEntity>

    @Query("SELECT * FROM albums ORDER BY name")
    suspend fun albums(): List<AlbumEntity>

    @Query("SELECT * FROM song_artist_cross_ref")
    suspend fun songArtistRefs(): List<SongArtistCrossRef>

    @Query("SELECT artistId FROM song_artist_cross_ref WHERE songId = :songId")
    suspend fun artistIdsForSong(songId: Int): List<Int>

    @Query(
        """
        SELECT a.artistId AS artistId, a.name AS name,
            COUNT(DISTINCT sar.songId) AS songCount,
            COUNT(DISTINCT s.albumId) AS albumCount,
            (SELECT s2.albumArtCacheUri FROM songs s2
                JOIN song_artist_cross_ref sar2 ON s2.id = sar2.songId
                WHERE sar2.artistId = a.artistId AND s2.albumArtCacheUri IS NOT NULL
                LIMIT 1) AS coverUri
        FROM artists a
        LEFT JOIN song_artist_cross_ref sar ON a.artistId = sar.artistId
        LEFT JOIN songs s ON s.id = sar.songId
        GROUP BY a.artistId, a.name
        ORDER BY a.name
        """,
    )
    suspend fun artistCatalog(): List<ArtistCatalogRow>

    @Query(
        """
        SELECT al.albumId AS albumId, al.name AS name,
            COUNT(s.id) AS songCount,
            (SELECT s2.albumArtCacheUri FROM songs s2
                WHERE s2.albumId = al.albumId AND s2.albumArtCacheUri IS NOT NULL
                LIMIT 1) AS coverUri
        FROM albums al
        LEFT JOIN songs s ON s.albumId = al.albumId
        GROUP BY al.albumId, al.name
        ORDER BY al.name
        """,
    )
    suspend fun albumCatalog(): List<AlbumCatalogRow>

    @Query(
        """
        SELECT DISTINCT s.albumId AS albumId, a.name AS artistName
        FROM songs s
        JOIN song_artist_cross_ref sar ON s.id = sar.songId
        JOIN artists a ON a.artistId = sar.artistId
        WHERE s.albumId IS NOT NULL
        """,
    )
    suspend fun albumArtistNames(): List<AlbumArtistNameRow>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongArtistRefs(refs: List<SongArtistCrossRef>)

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

    @Query("DELETE FROM song_artist_cross_ref")
    suspend fun deleteAllSongArtistRefs()

    @Query("DELETE FROM artists")
    suspend fun deleteAllArtists()

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    @Query("DELETE FROM artists WHERE artistId NOT IN (SELECT DISTINCT artistId FROM song_artist_cross_ref)")
    suspend fun deleteOrphanArtists()

    @Query("DELETE FROM albums WHERE albumId NOT IN (SELECT DISTINCT albumId FROM songs WHERE albumId IS NOT NULL)")
    suspend fun deleteOrphanAlbums()

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

    /**
     * 根据歌曲列表重建歌手/专辑目录与歌曲-歌手关联。
     *
     * 幂等：插入不存在的歌手/专辑，随后重建全部关联。
     * 调用方需在写入歌曲后调用，以保证曲库持久化的歌手/专辑与歌曲保持一致。
     */
    @Transaction
    suspend fun syncLibraryCatalog(songs: List<Song>) {
        if (songs.isEmpty()) {
            deleteAllSongArtistRefs()
            deleteAllArtists()
            deleteAllAlbums()
            return
        }

        // 收集全部原子歌手与专辑名
        val artistNames = songs
            .flatMap { it.parsedArtists }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val albumNames = songs
            .map { it.album }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        insertArtists(artistNames.map { ArtistEntity(name = it) })
        insertAlbums(albumNames.map { AlbumEntity(name = it) })

        val artistIdByName = artists().associate { it.name to it.artistId }
        val albumIdByName = albums().associate { it.name to it.albumId }

        // 重建全部关联与专辑外键
        deleteAllSongArtistRefs()
        val refs = songs.flatMap { song ->
            song.parsedArtists
                .mapNotNull { name -> artistIdByName[name]?.let { SongArtistCrossRef(song.id, it) } }
        }
        if (refs.isNotEmpty()) insertSongArtistRefs(refs)

        songs.forEach { song ->
            val newAlbumId = song.album.takeIf { it.isNotBlank() }?.let { albumIdByName[it] }
            if (newAlbumId != song.albumId) {
                upsertSongs(
                    listOf(
                        songs().first { it.id == song.id }.copy(albumId = newAlbumId),
                    ),
                )
            }
        }

        deleteOrphanArtists()
        deleteOrphanAlbums()
    }

    @Query("SELECT * FROM migration_state WHERE name = :name")
    suspend fun migrationState(name: String): MigrationStateEntity?

    @Upsert
    suspend fun upsertMigrationState(state: MigrationStateEntity)
}
