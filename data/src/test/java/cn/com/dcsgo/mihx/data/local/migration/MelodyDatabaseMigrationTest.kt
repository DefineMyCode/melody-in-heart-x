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

    @Test
    fun migration11To12AddsUserTagsEditedColumnWithNullDefault() {
        val name = dbName("11-12")
        helper.createDatabase(name, 11).apply {
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt, userTags) " +
                    "VALUES (5, 0.3, 0.7, '[0.1,0.2]', 1.0, 3, 120.0, 'v1', 42, 'emo')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 12, true, MelodyMigrations.MIGRATION_11_12)

        // 老数据无从判断"用户改没改模型" → NULL（诚实的"未知"），不是 false
        db.query("SELECT userTagsEdited FROM song_emotions WHERE songId = 5").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        // 新写入三态可用：true=主动纠正 / false=只是采纳
        db.execSQL("UPDATE song_emotions SET userTagsEdited = 1 WHERE songId = 5")
        db.execSQL(
            "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                "windowsAnalyzed, durationSec, modelVersion, analyzedAt, userTagsEdited) " +
                "VALUES (6, 0.1, 0.2, '[]', 0.0, 0, 0.0, 'user-only', 43, 0)"
        )
        db.query("SELECT userTagsEdited FROM song_emotions WHERE songId = 6").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration12To13StampsModelFingerprintAndClearsStaleCoordinates() {
        val name = dbName("12-13")
        helper.createDatabase(name, 12).apply {
            // 当前坐标系(旧格式, 无指纹)的一行 + 一行旧坐标系的行 + 一行用户校准
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt) " +
                    "VALUES (1, 0.3, 0.7, '[0.1,0.2]', 1.0, 3, 120.0, " +
                    "'yamnet-va-v4-rms', 42)"
            )
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt) " +
                    "VALUES (2, 0.1, 0.2, '[]', 0.0, 0, 0.0, 'yamnet-va-v3', 43)"
            )
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt, userValence, " +
                    "userArousal, userTags, userTagsEdited) " +
                    "VALUES (3, 0.3, 0.7, '[0.1,0.2]', 1.0, 3, 120.0, " +
                    "'yamnet-va-v4-rms', 44, 0.25, 0.675, '燃,神圣', 1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 13, true, MelodyMigrations.MIGRATION_12_13)

        // 当前坐标系的行补上内容指纹 -> 零重扫
        db.query("SELECT modelVersion FROM song_emotions WHERE songId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            // 写的是 **v4 时代的完整版本串**（那时的当前版本）,
            // 不是"今天的当前版本" —— 见 EmotionGroup.V4_MODEL_VERSION 的说明
            assertEquals(
                cn.com.dcsgo.mihx.core.model.EmotionGroup.V4_MODEL_VERSION,
                cursor.getString(0),
            )
        }
        // 旧坐标系的行**不**补指纹 -> 仍待扫(标签不同, 用旧锚点表)
        db.query("SELECT modelVersion FROM song_emotions WHERE songId = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("yamnet-va-v3", cursor.getString(0))
        }
        // 坐标被清空(改成读时派生), 词条保留 —— 词条才是真值
        db.query("SELECT userValence, userArousal, userTags, userTagsEdited " +
            "FROM song_emotions WHERE songId = 3").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertEquals("燃,神圣", cursor.getString(2))
            assertEquals(1, cursor.getInt(3))
        }
        db.close()
    }

    @Test
    fun migration14To15StampsV5CoordinateSystemWithoutTouchingCurves() {
        val name = dbName("14-15")
        helper.createDatabase(name, 14).apply {
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt, vocalScore) " +
                    "VALUES (1, 0.3, 0.7, '[0.1,0.2]', 1.0, 3, 120.0, " +
                    "'yamnet-va-v4-rms@b18e43ff', 42, 0.003)"
            )
            execSQL(
                "INSERT INTO song_emotions (songId, valence, arousal, curveJson, peakSec, " +
                    "windowsAnalyzed, durationSec, modelVersion, analyzedAt, vocalScore) " +
                    "VALUES (2, 0.1, 0.2, '[]', 0.0, 0, 0.0, 'yamnet-va-v3', 43, NULL)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 15, true, MelodyMigrations.MIGRATION_14_15)

        // v4 的行换成 v5 版本串 —— **零重扫**：
        // 人声成分补偿是读时派生的(库里存的仍是原始曲线), 曲线没变,
        // 所以老曲线在新坐标系下的解释本来就是对的, 只换标签。
        db.query("SELECT modelVersion FROM song_emotions WHERE songId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                cn.com.dcsgo.mihx.core.model.EmotionModelSpec.MODEL_VERSION,
                cursor.getString(0),
            )
        }
        // 更老的坐标系不动(它本来就该重扫)
        db.query("SELECT modelVersion FROM song_emotions WHERE songId = 2").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("yamnet-va-v3", cursor.getString(0))
        }
        // 曲线与 vocalScore 一律不动
        db.query("SELECT curveJson, vocalScore FROM song_emotions WHERE songId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[0.1,0.2]", cursor.getString(0))
            assertEquals(0.003, cursor.getDouble(1), 1e-6)
        }
        db.close()
    }

    @Test
    fun migration15To16RescalesHistoricalVotesToTheWeightedScale() {
        val name = dbName("15-16")
        helper.createDatabase(name, 15).apply {
            execSQL(
                "INSERT INTO word_preferences (word, likeVotes, dislikeVotes, updatedAt) " +
                    "VALUES ('emo', 0, 3, 42)"
            )
            execSQL(
                "INSERT INTO word_preferences (word, likeVotes, dislikeVotes, updatedAt) " +
                    "VALUES ('治愈', 2, 5, 43)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 16, true, MelodyMigrations.MIGRATION_15_16)

        // 历史票统一 x100 —— 新票是 15~100 票/次, 不缩放的话新票会立刻淹没历史
        db.query("SELECT likeVotes, dislikeVotes FROM word_preferences WHERE word = 'emo'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals(300, cursor.getInt(1))
            }
        db.query("SELECT likeVotes, dislikeVotes FROM word_preferences WHERE word = '治愈'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(200, cursor.getInt(0))
                assertEquals(500, cursor.getInt(1))
            }
        db.close()
    }
}
