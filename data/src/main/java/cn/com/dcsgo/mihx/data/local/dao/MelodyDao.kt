package cn.com.dcsgo.mihx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.EmotionTableStat
import kotlinx.coroutines.flow.Flow
import cn.com.dcsgo.mihx.data.local.entity.AlbumEntity
import cn.com.dcsgo.mihx.data.local.entity.ArtistEntity
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionLite
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity
import cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity

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

/** 按天聚合的时长行（day 为 'yyyy-MM-dd'，本地时区） */
data class DailyDurationRow(
    val day: String,
    val totalMs: Long,
)

/** 按歌曲聚合的有效播放次数行 */
data class SongPlayCountRow(
    val songId: Int,
    val playCount: Int,
)

/** 歌曲 id → 专辑实体 id（评审 2026-10-27 · M-7：只取这两列，不做 SELECT *） */
data class SongAlbumIdRow(
    val id: Int,
    val albumId: Int?,
)

@Dao
interface MelodyDao {
    @Query("SELECT * FROM songs ORDER BY id")
    suspend fun songs(): List<SongEntity>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun songCount(): Int

    /** 只要 id 列（评审 2026-10-27 · N-8）：`songs()` 是 `SELECT *`，为了拿 id 集合
     *  会把每首歌的 uri / albumArtCacheUri / lrcUri 等全部列物化进内存。 */
    @Query("SELECT id FROM songs")
    suspend fun songIds(): List<Int>

    /**
     * 只取 id 与 albumId 两列（评审 2026-10-27 · M-7）。
     *
     * `syncLibraryCatalog` 会**重建专辑目录并把 albumId 回写到 songs 表**，所以
     * 恢复流程必须在它之后重新读一次 albumId。但不需要 `SELECT *` ——
     * 那会把每首歌的 uri / albumArtCacheUri / lrcUri 等全部列物化进内存。
     */
    @Query("SELECT id, albumId FROM songs")
    suspend fun songAlbumIds(): List<SongAlbumIdRow>

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

    @Query("SELECT * FROM play_stats WHERE songId IN (:songIds)")
    suspend fun playStatsIn(songIds: List<Int>): List<PlayStatsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaybackEvent(event: PlaybackEventEntity)

    @Query(
        "SELECT COALESCE(SUM(durationMs), 0) FROM playback_events " +
            "WHERE startedAtMs >= :startMs AND startedAtMs < :endMs",
    )
    suspend fun totalDurationBetween(startMs: Long, endMs: Long): Long

    @Query(
        "SELECT COUNT(DISTINCT songId) FROM playback_events " +
            "WHERE startedAtMs >= :startMs AND startedAtMs < :endMs",
    )
    suspend fun distinctSongsBetween(startMs: Long, endMs: Long): Int

    @Query(
        """
        SELECT strftime('%Y-%m-%d', startedAtMs / 1000, 'unixepoch', 'localtime') AS day,
            COALESCE(SUM(durationMs), 0) AS totalMs
        FROM playback_events
        WHERE startedAtMs >= :startMs AND startedAtMs < :endMs
        GROUP BY day
        ORDER BY day
        """,
    )
    suspend fun dailyDurationsBetween(startMs: Long, endMs: Long): List<DailyDurationRow>

    @Query(
        """
        SELECT songId, COUNT(*) AS playCount
        FROM playback_events
        WHERE startedAtMs >= :startMs AND startedAtMs < :endMs AND isEffectivePlay = 1
        GROUP BY songId
        ORDER BY playCount DESC, songId ASC
        """,
    )
    suspend fun playCountsBetween(startMs: Long, endMs: Long): List<SongPlayCountRow>

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

    /** 原子自增有效播放次数，返回新值（不存在则插入 1）。Room/sqlite 语句级原子，无 lost update。 */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, rawPlayCount, totalDurationMs, lastPlayedAt)
        VALUES (:songId, 1, 0, 0, :now)
        ON CONFLICT(songId) DO UPDATE SET playCount = playCount + 1, lastPlayedAt = :now
        """
    )
    suspend fun incrementPlayCount(songId: Int, now: Long)

    /** 原子自增原始播放次数，返回新值（不存在则插入 1）。 */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, rawPlayCount, totalDurationMs, lastPlayedAt)
        VALUES (:songId, 0, 1, 0, :now)
        ON CONFLICT(songId) DO UPDATE SET rawPlayCount = rawPlayCount + 1, lastPlayedAt = :now
        """
    )
    suspend fun incrementRawPlayCount(songId: Int, now: Long)

    /** 原子累加累计播放时长（存在才累加，不存在则插入 durationMs）。 */
    @Query(
        """
        INSERT INTO play_stats (songId, playCount, rawPlayCount, totalDurationMs, lastPlayedAt)
        VALUES (:songId, 0, 0, :durationMs, :now)
        ON CONFLICT(songId) DO UPDATE SET totalDurationMs = totalDurationMs + :durationMs, lastPlayedAt = :now
        """
    )
    suspend fun addPlayDuration(songId: Int, durationMs: Long, now: Long)

    @Upsert
    suspend fun upsertQuickSkipSongs(songs: List<QuickSkipSongEntity>)

    @Upsert
    suspend fun upsertQuickSkipSong(song: QuickSkipSongEntity)

    @Upsert
    suspend fun upsertQuickSkipShortPlay(count: QuickSkipShortPlayEntity)

    /**
     * 原子自增短播放计数（不存在则插入 1）。
     *
     * 评审 2026-10-27 · M-22：原实现是「读 → +1 → 整行 upsert」，并发下两个
     * 调用读到同一基数、后写覆盖先写 → **丢一次计数**，而该计数驱动
     * 「累计 2 次自动加入秒切列表」的规则，丢计数会推迟自动加入。
     *
     * 实现用「幂等建行 + 原子自增」两步（而不是 `INSERT ... ON CONFLICT DO UPDATE`）：
     * 后者要 SQLite ≥ 3.24（UPSERT），而部分 JVM 测试环境的 SQLite 还不支持；
     * 两步写法在任何 SQLite 版本上都能跑，且同样无 lost update ——
     * `INSERT OR IGNORE` 保证行存在（已存在则忽略），`UPDATE ... SET count = count + 1`
     * 是语句级原子。调用方必须**先 ensure 再 increment**。
     */
    @Query(
        """
        INSERT OR IGNORE INTO quick_skip_short_play_counts (songId, count, updatedAt)
        VALUES (:songId, 0, :now)
        """
    )
    suspend fun ensureQuickSkipShortPlay(songId: Int, now: Long)

    /** 原子自增（语句级）：`count = count + 1`，饱和在 Int 上限不溢出。 */
    @Query(
        """
        UPDATE quick_skip_short_play_counts
        SET count = MIN(count + 1, 2147483647), updatedAt = :now
        WHERE songId = :songId
        """
    )
    suspend fun incrementQuickSkipShortPlay(songId: Int, now: Long)

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

    @Query("DELETE FROM play_stats WHERE songId IN (:songIds)")
    suspend fun deletePlayStatsForSongs(songIds: List<Int>)

    @Query("DELETE FROM playback_events WHERE songId IN (:songIds)")
    suspend fun deletePlaybackEventsForSongs(songIds: List<Int>)

    /**
     * 删除超出保留期的播放事件（评审 2026-10-27 · M-6）。
     *
     * playback_events 是纯追加表，本项目原本**没有任何清理逻辑**——它随应用
     * 生命周期无限增长，而每次删歌/去重/文件校验的「按 songId 清理」都要扫这张表。
     * 配合 `index_playback_events_songId` 索引，清理成本回到可控范围。
     */
    @Query("DELETE FROM playback_events WHERE startedAtMs < :beforeMs")
    suspend fun deletePlaybackEventsBefore(beforeMs: Long): Int

    @Query("DELETE FROM quick_skip_songs WHERE songId IN (:songIds)")
    suspend fun deleteQuickSkipSongsFor(songIds: List<Int>)

    @Query("DELETE FROM quick_skip_short_play_counts WHERE songId IN (:songIds)")
    suspend fun deleteQuickSkipShortPlaysFor(songIds: List<Int>)

    @Query("DELETE FROM song_emotions WHERE songId IN (:songIds)")
    suspend fun deleteSongEmotionsFor(songIds: List<Int>)

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

        // 专辑外键回填:一次性读入歌曲实体后批量 upsert。
        // 早期实现是「逐首歌全表查询 + 单条写入」,曲库规模上千首时会退化成上千次查询(N+1)。
        val songEntityById = songs().associateBy { it.id }
        val albumIdUpdates = songs.mapNotNull { song ->
            val newAlbumId = song.album.takeIf { it.isNotBlank() }?.let { albumIdByName[it] }
            if (newAlbumId == song.albumId) return@mapNotNull null
            songEntityById[song.id]?.copy(albumId = newAlbumId)
        }
        if (albumIdUpdates.isNotEmpty()) upsertSongs(albumIdUpdates)

        deleteOrphanArtists()
        deleteOrphanAlbums()
    }

    @Query("SELECT * FROM migration_state WHERE name = :name")
    suspend fun migrationState(name: String): MigrationStateEntity?

    @Upsert
    suspend fun upsertMigrationState(state: MigrationStateEntity)

    // ---------- song_emotions ----------

    @Query("SELECT * FROM song_emotions WHERE songId = :songId")
    suspend fun songEmotion(songId: Int): SongEmotionEntity?

    /**
     * song_emotions 全量查询（轻量投影, 不含 embeddingB64）。
     *
     * P0（2026-10-24）：旧实现是 `SELECT *`，会把整库 embedding（约 5.3KB/首 base64）
     * 物化成字符串再由仓储层解码成 FloatArray，而读路径（情绪词条缓存/曲库情绪 Tab/
     * 时段统计）只用到 V/A 曲线与用户校准字段——整库白物化上百 MB。
     * 改为显式列投影，Room 直接跳过 embeddingB64 列；表结构不变，无需迁移。
     */
    @Query(
        "SELECT songId, valence, arousal, curveJson, peakSec, windowsAnalyzed, " +
            "durationSec, modelVersion, analyzedAt, userValence, userArousal, userTags, " +
            "userTagsEdited, vocalScore FROM song_emotions"
    )
    suspend fun allSongEmotions(): List<SongEmotionLite>

    /**
     * song_emotions 可观察流（Room 表失效自动重发）：PlayerRuntime 情绪词条缓存的数据源（M-2，评审 2026-10-15）。
     * 轻量投影同 [allSongEmotions]：批扫每写一首都会触发整表重发，
     * 投影让每次重发不再携带整库 embedding。
     */
    @Query(
        "SELECT songId, valence, arousal, curveJson, peakSec, windowsAnalyzed, " +
            "durationSec, modelVersion, analyzedAt, userValence, userArousal, userTags, " +
            "userTagsEdited, vocalScore FROM song_emotions"
    )
    fun observeAllSongEmotions(): Flow<List<SongEmotionLite>>

    /**
     * 全库已知最大 songId（含统计/播放事件/秒切/情绪等关联表的残留引用）。
     * M-4（评审 2026-10-15）：restoreFromRoom 以此为高水位分配 nextId，
     * 防止被删歌曲（尤其旧版本删除不清理关联数据时）的 id 在重启后被新导入歌曲复用、继承其统计。
     */
    @Query(
        "SELECT MAX(id) FROM (" +
            "SELECT MAX(id) AS id FROM songs " +
            "UNION ALL SELECT MAX(songId) AS id FROM play_stats " +
            "UNION ALL SELECT MAX(songId) AS id FROM playback_events " +
            "UNION ALL SELECT MAX(songId) AS id FROM quick_skip_songs " +
            "UNION ALL SELECT MAX(songId) AS id FROM quick_skip_short_play_counts " +
            "UNION ALL SELECT MAX(songId) AS id FROM song_emotions" +
            ")",
    )
    suspend fun maxKnownSongId(): Int?

    @Query("SELECT songId, modelVersion FROM song_emotions")
    suspend fun songEmotionVersions(): List<SongEmotionVersion>

    /**
     * 已分析但**缺人声分数**的行（2026-10-25 之前分析的数据）。
     *
     * vocalScore 为 NULL = 这一行是引入人声分数之前算的, 缺了条件标定要用的变量
     * → 需要补扫一次。补完后 vocalScore 非空, 这条查询自然为空, 不会反复重扫。
     *
     * 只取 windowsAnalyzed > 0: user-only 行（分析失败但用户手动标了词）由失败重试
     * 机制管，不算"待补扫"。
     */
    @Query("SELECT songId FROM song_emotions WHERE vocalScore IS NULL AND windowsAnalyzed > 0")
    suspend fun songIdsMissingVocalScore(): List<Int>

    /** 已用户校准的歌曲数（以**词条**为准 —— 坐标不再落库） */
    @Query(
        "SELECT COUNT(*) FROM song_emotions " +
            "WHERE userTags IS NOT NULL AND userTags <> ''"
    )
    suspend fun emotionCorrectionCount(): Int

    /**
     * 情绪表轻量统计（评审 2026-10-27 · M-3）。
     *
     * 轮询路径用它替代「整表读 songId→modelVersion + 整表读逐首时间戳」：
     * 行数 + 最近一次分析时间足以作为「需要重建列表」的指纹。
     */
    @Query(
        "SELECT COUNT(*) AS count, IFNULL(MAX(analyzedAt), 0) AS maxAnalyzedAt FROM song_emotions"
    )
    suspend fun emotionTableStat(): EmotionTableStat

    /**
     * 最近 N 条分析完成时间（**降序**）—— 轮询算「上一首耗时 / 近 N 首平均耗时」用。
     *
     * 只取最近若干条而不是全表：1000 首库的全表时间戳每 2 秒物化一次没有必要。
     */
    @Query(
        "SELECT analyzedAt FROM song_emotions WHERE windowsAnalyzed > 0 " +
            "ORDER BY analyzedAt DESC LIMIT :limit"
    )
    suspend fun emotionRecentAnalyzedTimestamps(limit: Int): List<Long>

    @Upsert
    suspend fun upsertSongEmotion(emotion: SongEmotionEntity)

    /**
     * 原子写入模型结果，**保留行上已有的用户校准**（评审 2026-10-27 · M-23）。
     *
     * 原来的实现是「读旧行 → 在内存里合并 userTags → 整行 upsert」，
     * 读与写之间有窗口：用户在那一瞬间保存校准，扫描随后用**陈旧的** userTags
     * 覆写回去 —— 用户亲手标的词条**静默消失**，且他不知道为什么。
     *
     * 现在把合并规则搬进 SQL 的 CASE 表达式，一条语句完成：
     * 新值带词条 → 用新值（用户主动标记的场景）；
     * 新值不带词条 → 保留行上已有的词条（重扫不吞校准）。
     * 无论用户的校准落在读之前还是读之后，都不会被覆盖 —— **窗口消失**。
     *
     * 坐标不继承：坐标是派生值（词条 → 该曲线坐标系的锚点均值），读时现算，
     * 存下来的坐标会在曲线换坐标系后过期（"手动点过期"的根源）。
     *
     * @return 受影响行数（0 = 该 songId 尚无行，调用方需走插入路径）
     */
    @Query(
        """
        UPDATE song_emotions SET
            valence = :valence,
            arousal = :arousal,
            curveJson = :curveJson,
            peakSec = :peakSec,
            windowsAnalyzed = :windowsAnalyzed,
            durationSec = :durationSec,
            modelVersion = :modelVersion,
            analyzedAt = :analyzedAt,
            embeddingB64 = :embeddingB64,
            userValence = :userValence,
            userArousal = :userArousal,
            userTags = CASE
                WHEN :userTags IS NULL OR :userTags = '' THEN userTags
                ELSE :userTags END,
            userTagsEdited = CASE
                WHEN :userTags IS NULL OR :userTags = '' THEN userTagsEdited
                ELSE :userTagsEdited END,
            vocalScore = :vocalScore
        WHERE songId = :songId
        """
    )
    suspend fun updateSongEmotionPreservingUserTags(
        songId: Int,
        valence: Float,
        arousal: Float,
        curveJson: String,
        peakSec: Float,
        windowsAnalyzed: Int,
        durationSec: Float,
        modelVersion: String,
        analyzedAt: Long,
        embeddingB64: String?,
        userValence: Float?,
        userArousal: Float?,
        userTags: String?,
        userTagsEdited: Boolean?,
        vocalScore: Float?,
    ): Int

    /**
     * 用户校准写入：**只写词条**。
     *
     * 坐标列一并置 NULL —— 坐标是派生值(词条 → 该曲线坐标系的锚点均值),
     * 读时现算(见 SongEmotion.effectiveValence)。存下来的坐标会在曲线
     * 换坐标系后过期(实测 3 首用户校准的歌坐标全错)。
     */
    @Query(
        "UPDATE song_emotions SET userValence = NULL, userArousal = NULL, " +
            "userTags = :tags, userTagsEdited = :edited WHERE songId = :songId"
    )
    suspend fun updateSongEmotionCorrection(
        songId: Int,
        tags: String,
        edited: Boolean?,
    ): Int

    /**
     * 用户-only 校准**插入**（评审 2026-10-27 · M-23）：分析失败/未分析的歌曲没有
     * emotion 行，UPDATE 语句静默无效 —— 用户手动标记需要无记录时 INSERT 一条
     * 仅含 user 字段的行（曲线/embedding 留空，modelVersion 记 "user-only"，坐标不落库）。
     *
     * 用 `INSERT OR IGNORE` 而不是 `@Upsert`：后者在「行其实已存在」时会把**整行**
     * 替换成 user-only 的空曲线 —— 用户的校准反而把分析结果**擦掉**了。
     * `INSERT OR IGNORE` 在行已存在时什么都不做（由调用方改走 UPDATE 路径）。
     *
     * @return 实际插入的行数（0 = 行已存在）
     */
    @Query(
        """
        INSERT OR IGNORE INTO song_emotions (
            songId, valence, arousal, curveJson, peakSec, windowsAnalyzed, durationSec,
            modelVersion, analyzedAt, embeddingB64, userValence, userArousal,
            userTags, userTagsEdited, vocalScore
        ) VALUES (
            :songId, 0, 0, '[]', 0, 0, 0, 'user-only', :now, NULL, NULL, NULL,
            :tags, :edited, NULL
        )
        """
    )
    suspend fun insertUserOnlyCorrectionIfAbsent(
        songId: Int,
        tags: String,
        edited: Boolean?,
        now: Long,
    )

    @Query("DELETE FROM song_emotions WHERE songId = :songId")
    suspend fun deleteSongEmotion(songId: Int)

    @Query(
        "UPDATE song_emotions SET userValence = NULL, userArousal = NULL, userTags = NULL, " +
            "userTagsEdited = NULL WHERE songId = :songId"
    )
    suspend fun clearSongEmotionCorrection(songId: Int)

    // ─────────── word_preferences（2026-10-20 探索播放：情绪词条收听偏好计票） ───────────

    /** word_preferences 可观察流（Room 表失效自动重发）：PlayerRuntime 探索播放缓存的数据源 */
    @Query("SELECT * FROM word_preferences")
    fun observeAllWordPreferences(): Flow<List<WordPreferenceEntity>>

    @Query("SELECT * FROM word_preferences")
    suspend fun allWordPreferences(): List<WordPreferenceEntity>

    /** 批量 upsert：读当前值 → 票数 +1 → 写回，由仓储层完成（DAO 只做整行替换） */
    @Upsert
    suspend fun upsertWordPreferences(items: List<WordPreferenceEntity>)

    /**
     * 原子累加某个词条的喜欢/不喜欢票（不存在则按 delta 建行）。
     *
     * 评审 2026-10-27 · M-22：原实现是「读整表 → 算新票数 → 整表 upsert」，
     * 两个并发的结算（跳过=不喜欢 / 听完=喜欢）会读到同一基数、后写覆盖先写
     * → **票数丢失**，探索播放的偏好归属会静默漂移。
     *
     * 饱和加法保留：`MIN(x, 2147483647)` 让票数停在 Int 上限而不是溢出成
     * 负数（负数会让「票多者胜」的判定静默翻转，比溢出本身更糟）。
     *
     * 与 [addWordVotesDelta] 配套：调用方先 [ensureWordPreference] 再自增。
     */
    @Query(
        """
        INSERT OR IGNORE INTO word_preferences (word, likeVotes, dislikeVotes, updatedAt)
        VALUES (:word, 0, 0, :now)
        """
    )
    suspend fun ensureWordPreference(word: String, now: Long)

    /** 原子累加票数（语句级）：`x = MIN(x + delta, Int 上限)`。 */
    @Query(
        """
        UPDATE word_preferences
        SET likeVotes = MIN(likeVotes + :likeDelta, 2147483647),
            dislikeVotes = MIN(dislikeVotes + :dislikeDelta, 2147483647),
            updatedAt = :now
        WHERE word = :word
        """
    )
    suspend fun addWordVotesDelta(word: String, likeDelta: Int, dislikeDelta: Int, now: Long)

    @Query("DELETE FROM word_preferences")
    suspend fun clearWordPreferences()
}

/** analyzedVersions() 的轻量投影. */
data class SongEmotionVersion(
    val songId: Int,
    val modelVersion: String,
)
