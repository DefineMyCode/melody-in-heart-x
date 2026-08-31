package cn.com.dcsgo.mihx.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.migration.LegacyJsonMigration
import cn.com.dcsgo.mihx.data.local.migration.SharedPreferencesLegacyJsonMigration
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideMelodyDatabase(
        @ApplicationContext context: Context,
    ): MelodyDatabase {
        return Room.databaseBuilder(
            context,
            MelodyDatabase::class.java,
            "melody.db",
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .addMigrations(
                MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
            )
            .build()
    }

    @Provides
    fun provideMelodyDao(database: MelodyDatabase): MelodyDao = database.melodyDao()

    @Provides
    @Singleton
    fun provideLegacyJsonMigration(
        @ApplicationContext context: Context,
        dao: MelodyDao,
    ): LegacyJsonMigration = SharedPreferencesLegacyJsonMigration(context, dao)

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `quick_skip_short_play_counts` (
                    `songId` INTEGER NOT NULL,
                    `count` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`songId`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `lrcUri` TEXT")
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `album` TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 歌手表
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `artists` (" +
                    "`artistId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_name` ON `artists` (`name`)")
            // 专辑表
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `albums` (" +
                    "`albumId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL)"
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_name` ON `albums` (`name`)")
            // 歌曲-歌手多对多
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_artist_cross_ref` (" +
                    "`songId` INTEGER NOT NULL, " +
                    "`artistId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`songId`, `artistId`))"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_cross_ref_artistId` ON `song_artist_cross_ref` (`artistId`)")
            // 歌曲新增专辑外键
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `albumId` INTEGER")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 播放会话事件表：按日/周/月聚合时长与歌曲热度榜
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`songId` INTEGER NOT NULL, " +
                    "`startedAtMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, " +
                    "`isEffectivePlay` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_events_startedAtMs` " +
                    "ON `playback_events` (`startedAtMs`)"
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 歌曲新增时长列（毫秒），用于曲库列表/详情展示
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // playback_events 复合索引：覆盖全部按时间范围聚合的查询（时长/去重歌数/逐日/歌曲榜）
            // 并补齐「新装库无索引」的历史不一致。先替换 5→6 创建的单列 startedAtMs 索引
            // （它是新复合索引的前缀，继续保留会造成冗余）。
            db.execSQL("DROP INDEX IF EXISTS `index_playback_events_startedAtMs`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playback_events_startedAtMs_isEffectivePlay_songId` " +
                    "ON `playback_events` (`startedAtMs`, `isEffectivePlay`, `songId`)"
            )
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 歌曲情绪分析结果表（整曲逐窗 V/A 曲线）
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `song_emotions` (" +
                    "`songId` INTEGER NOT NULL, " +
                    "`valence` REAL NOT NULL, " +
                    "`arousal` REAL NOT NULL, " +
                    "`curveJson` TEXT NOT NULL, " +
                    "`peakSec` REAL NOT NULL, " +
                    "`windowsAnalyzed` INTEGER NOT NULL, " +
                    "`durationSec` REAL NOT NULL, " +
                    "`modelVersion` TEXT NOT NULL, " +
                    "`analyzedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`songId`))"
            )
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 个性化闭环: embedding(端侧kNN泛化) + 用户校准坐标/词条
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `embeddingB64` TEXT")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userValence` REAL")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userArousal` REAL")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userTags` TEXT")
        }
    }
}
