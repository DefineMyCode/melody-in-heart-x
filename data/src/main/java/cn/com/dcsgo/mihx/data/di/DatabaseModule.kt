package cn.com.dcsgo.mihx.data.di

import android.content.Context
import androidx.room.Room
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.migration.LegacyJsonMigration
import cn.com.dcsgo.mihx.data.local.migration.MelodyMigrations
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
            .addMigrations(*MelodyMigrations.ALL)
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
}
