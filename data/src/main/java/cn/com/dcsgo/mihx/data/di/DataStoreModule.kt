package cn.com.dcsgo.mihx.data.di

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import cn.com.dcsgo.mihx.data.repository.PlayerSettingsKeys
import cn.com.dcsgo.mihx.data.repository.playerSettingsDataStore
import cn.com.dcsgo.mihx.data.repository.timeSlotConfigDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerSettingsStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TimeSlotConfigStore

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LegacyMusicPlayerPreferences

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    @PlayerSettingsStore
    fun providePlayerSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.applicationContext.playerSettingsDataStore

    @Provides
    @Singleton
    @TimeSlotConfigStore
    fun provideTimeSlotConfigDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.applicationContext.timeSlotConfigDataStore

    @Provides
    @Singleton
    @LegacyMusicPlayerPreferences
    fun provideLegacyMusicPlayerPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.applicationContext.getSharedPreferences(
        PlayerSettingsKeys.LEGACY_PREFS_NAME,
        Context.MODE_PRIVATE,
    )
}
