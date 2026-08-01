package cn.com.dcsgo.mihx.data.di

import android.content.Context
import androidx.room.Room
import cn.com.dcsgo.mihx.data.database.MelodyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MelodyDatabase = Room.databaseBuilder(
        context,
        MelodyDatabase::class.java,
        MelodyDatabase.DATABASE_NAME,
    ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideMelodyDao(db: MelodyDatabase) = db.melodyDao()
}
