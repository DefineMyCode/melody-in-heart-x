package cn.com.dcsgo.mihx.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema migrations for [MelodyDatabase].
 *
 * - 1 -> 2: add `play_stats.totalPlayedMs` (P5-C listening-time statistics). Existing rows default
 *   to 0 so no playback-count data is lost on upgrade.
 */
object MelodyDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE play_stats ADD COLUMN totalPlayedMs INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
}
