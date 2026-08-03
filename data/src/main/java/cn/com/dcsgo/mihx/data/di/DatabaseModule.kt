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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
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
}
