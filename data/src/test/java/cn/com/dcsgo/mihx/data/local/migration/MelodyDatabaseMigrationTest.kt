package cn.com.dcsgo.mihx.data.local.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * m-4（评审 2026-10-15）：手写迁移（v1→v10）回归。
 *
 * 评审指出 9 个手写 Migration 一直没有迁移测试，与实体定义的漂移只能靠人眼比对
 * （评审当时是逐表对照 10.json 与 DatabaseModule 建表语句）。
 * 本测试用 MigrationTestHelper + 导出的 schema JSON（data/schemas/）：
 * - runMigrationsAndValidate 会把迁移结果与目标版本 schema 逐表校验（列/索引/主键全对齐），
 *   任何手写 SQL 与 @Entity 定义的漂移都会失败；
 * - 每步迁移前插入旧版本形态的数据，迁移后断言数据存续/新列默认值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MelodyDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MelodyDatabase::class.java,
    )

    private fun dbName(name: String) = "migration-test-$name.db"

    /** v1 形态的 songs 行（v1 无 album/albumId/lrcUri/durationMs 列） */
    private fun insertV1Song(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Int, title: String) {
        db.execSQL(
            "INSERT INTO songs (id, title, artist, sampleRate, uri, displayName, mimeType, " +
                "lastModified, size, sourceTreeUri, albumArtCacheUri, importedAt) " +
                "VALUES ($id, '$title', 'Artist', 44100, NULL, 'song-$id.mp3', 'audio/mpeg', " +
                "NULL, NULL, NULL, NULL, 1)"
        )
    }

    @Test
    fun migrateAllVersionsFrom1To10KeepsSongsAndFillsNewColumnDefaults() {
        val name = dbName("full-path")
        helper.createDatabase(name, 1).use { db ->
            insertV1Song(db, id = 1, title = "第一首歌")
            insertV1Song(db, id = 2, title = "第二首歌")
        }

        val db = helper.runMigrationsAndValidate(name, 10, true, *MelodyMigrations.ALL)

        // 旧行存续 + 各版本新增列的默认值符合迁移语义
        db.query("SELECT title, album, durationMs, lrcUri FROM songs WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("第一首歌", cursor.getString(0))
            // 3→4: album NOT NULL DEFAULT ''
            assertEquals("", cursor.getString(1))
            // 6→7: durationMs NOT NULL DEFAULT 0
            assertEquals(0, cursor.getLong(2))
            // 2→3: lrcUri 可空列，旧行为 NULL
            assertTrue(cursor.isNull(3))
        }
        db.query("SELECT COUNT(*) FROM songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration1To2CreatesShortPlayCountsTable() {
        val name = dbName("1-2")
        helper.createDatabase(name, 1).close()

        val db = helper.runMigrationsAndValidate(name, 2, true, MelodyMigrations.MIGRATION_1_2)

        db.execSQL(
            "INSERT INTO quick_skip_short_play_counts (songId, count, updatedAt) VALUES (7, 2, 1)"
        )
        db.query("SELECT count FROM quick_skip_short_play_counts WHERE songId = 7").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration4To5CreatesArtistAlbumCatalogTables() {
        val name = dbName("4-5")
        helper.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO songs (id, title, artist, sampleRate, uri, displayName, mimeType, " +
                    "lastModified, size, sourceTreeUri, albumArtCacheUri, album, importedAt) " +
                    "VALUES (1, 't', '周杰伦', 44100, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '专辑A', 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 5, true, MelodyMigrations.MIGRATION_4_5)

        db.execSQL("INSERT INTO artists (name) VALUES ('周杰伦')")
        db.execSQL("INSERT INTO albums (name) VALUES ('专辑A')")
        db.execSQL("INSERT INTO song_artist_cross_ref (songId, artistId) VALUES (1, 1)")
        db.query("SELECT COUNT(*) FROM artists").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration5To6CreatesPlaybackEventsTable() {
        val name = dbName("5-6")
        helper.createDatabase(name, 5).close()

        val db = helper.runMigrationsAndValidate(name, 6, true, MelodyMigrations.MIGRATION_5_6)

        db.execSQL(
            "INSERT INTO playback_events (songId, startedAtMs, durationMs, isEffectivePlay) " +
                "VALUES (1, 1000, 60000, 1)"
        )
        db.query("SELECT songId, durationMs FROM playback_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals(60_000L, cursor.getLong(1))
        }
        db.close()
    }

    @Test
    fun migration8To10AddsEmotionColumnsWithNullDefaults() {
        val name = dbName("8-10")

        // 8→9：创建 song_emotions 表（v8 尚无此表）
        helper.createDatabase(name, 8).close()
        val db9 = helper.runMigrationsAndValidate(name, 9, true, MelodyMigrations.MIGRATION_8_9)
        db9.execSQL(
            "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                "windowsAnalyzed, durationSec, modelVersion, analyzedAt) " +
                "VALUES (5, 0.3, 0.7, '[0.1,0.2]', 1.0, 3, 120.0, 'v1', 42)"
        )
        db9.close()

        // 9→10：新增四个个性化列，旧行均为 NULL（"未校准" 语义）
        val db = helper.runMigrationsAndValidate(name, 10, true, MelodyMigrations.MIGRATION_9_10)

        db.query(
            "SELECT embeddingB64, userValence, userArousal, userTags FROM song_emotions WHERE songId = 5"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
            assertNull(cursor.getString(3))
        }
        db.close()
    }

    @Test
    fun migration10To11CreatesWordPreferencesTable() {
        val name = dbName("10-11")
        helper.createDatabase(name, 10).close()

        val db = helper.runMigrationsAndValidate(name, 11, true, MelodyMigrations.MIGRATION_10_11)

        db.execSQL(
            "INSERT INTO word_preferences (word, likeVotes, dislikeVotes, updatedAt) VALUES ('静谧', 3, 1, 42)"
        )
        db.query("SELECT likeVotes, dislikeVotes FROM word_preferences WHERE word = '静谧'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
            assertEquals(1, cursor.getInt(1))
        }
        db.close()
    }
}
