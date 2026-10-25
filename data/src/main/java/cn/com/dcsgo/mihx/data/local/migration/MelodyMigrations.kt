package cn.com.dcsgo.mihx.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 手写 Room 迁移（v1 → v10）。
 *
 * m-4（评审 2026-10-15）：从 DatabaseModule 抽出为公开对象 —— 迁移回归测试
 * （[cn.com.dcsgo.mihx.data.local.migration.MelodyDatabaseMigrationTest]）需要直接引用它们，
 * 与 DatabaseModule 保持同一实例，避免「测试的迁移」与「线上迁移」漂移。
 */
object MelodyMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
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

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `lrcUri` TEXT")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `album` TEXT NOT NULL DEFAULT ''")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
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

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 播放会话事件表：按日/周/月聚合时长与歌曲热度榜
            //
            // m-4 迁移测试暴露的历史不一致（CI Linux 必现）：v6 的导出 schema
            // （v6.json，indices=[]）里 playback_events **没有**单列 startedAtMs 索引——
            // v6 时代 entity 未声明 @Index，新装库走 entity 建表无索引，而旧迁移库走
            // 下面的 CREATE INDEX 有索引，直到 7→8 才统一为复合索引。
            // 因此这里不再创建单列索引（与 v6 schema 一致）；7→8 直接建复合索引，
            // 其 DROP IF EXISTS 保留，兼容真实走过旧 5→6 迁移的老设备。
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playback_events` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`songId` INTEGER NOT NULL, " +
                    "`startedAtMs` INTEGER NOT NULL, " +
                    "`durationMs` INTEGER NOT NULL, " +
                    "`isEffectivePlay` INTEGER NOT NULL)"
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 歌曲新增时长列（毫秒），用于曲库列表/详情展示
            db.execSQL("ALTER TABLE `songs` ADD COLUMN `durationMs` INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
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

    val MIGRATION_8_9 = object : Migration(8, 9) {
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

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 个性化闭环: embedding(端侧kNN泛化) + 用户校准坐标/词条
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `embeddingB64` TEXT")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userValence` REAL")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userArousal` REAL")
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userTags` TEXT")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 探索播放（2026-10-20）：情绪词条收听偏好计票表
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `word_preferences` (" +
                    "`word` TEXT NOT NULL, " +
                    "`likeVotes` INTEGER NOT NULL, " +
                    "`dislikeVotes` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`word`))"
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 用户是否「不认同」模型结论（2026-10-24）：区分「主动纠正」与「直接采纳」。
            // 可空且无 DEFAULT —— 老数据无从判断改没改（迁移前没有这个信息），
            // 存 NULL 才是诚实的；新建库由 @Entity 生成同一形态（INTEGER 可空），
            // 故新旧 schema 完全一致。
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `userTagsEdited` INTEGER")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 情绪坐标系版本改成 "<标签>@<内容指纹>"（2026-10-25）。
            //
            // ① 版本串补指纹: 老行是**当前资产**产出的(本次发布没换模型资产,
            //    :core:model 的 generateEmotionModelSpec 校验过哈希), 所以补当前指纹
            //    是事实陈述 -> 零重扫。
            //    以后换了资产, 指纹自动变, 老行与新串不等 -> 自动重扫(无需新迁移)。
            // ② 坐标列置空: 用户校准的坐标改成**读时派生**(词条 → 该曲线坐标系的
            //    锚点均值), 存的坐标会在曲线换坐标系后过期(手动点过期问题的根源)。
            //    词条 userTags 不动 —— 它才是真值。
            db.execSQL(
                "UPDATE `song_emotions` SET `modelVersion` = ? WHERE `modelVersion` = ?",
                arrayOf(
                    cn.com.dcsgo.mihx.core.model.EmotionModelSpec.MODEL_VERSION,
                    cn.com.dcsgo.mihx.core.model.EmotionModelSpec.LABEL,
                ),
            )
            db.execSQL(
                "UPDATE `song_emotions` SET `userValence` = NULL, `userArousal` = NULL " +
                    "WHERE `userTags` IS NOT NULL AND `userTags` <> ''",
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 人声分数（2026-10-25）：valence 存在「人声偏置」——器乐曲被平均判低
            // 0.154（实测 MWU p=0.011）。分析器本来就把 YAMNet 的 521 个 AudioSet
            // 类别分数算出来了，只是以前扔掉；这里把用人声类的分数和落库，
            // 作为条件标定与「纯器乐/有人声」标记的输入。
            // 可空且无 DEFAULT：老行没有这个值，存 NULL 才是诚实的。
            db.execSQL("ALTER TABLE `song_emotions` ADD COLUMN `vocalScore` REAL")
        }
    }

    /** 声明于末尾：object 属性按声明顺序初始化，需引用上方全部迁移 */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
        MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
        MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
    )
}
