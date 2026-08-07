package cn.com.dcsgo.mihx.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.migration.LegacyJsonMigration
import cn.com.dcsgo.mihx.data.repository.AlbumArtRepositoryAdapter
import cn.com.dcsgo.mihx.data.repository.MediaMetadataRepository
import cn.com.dcsgo.mihx.data.repository.MusicImportRepositoryAdapter
import cn.com.dcsgo.mihx.data.repository.MusicRepository
import cn.com.dcsgo.mihx.data.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.data.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.data.repository.PlaylistResumeDataStore
import cn.com.dcsgo.mihx.data.repository.PlaylistRepositoryAdapter
import cn.com.dcsgo.mihx.data.repository.QuickSkipSongsRepository
import cn.com.dcsgo.mihx.data.repository.SongRepositoryAdapter
import cn.com.dcsgo.mihx.domain.repository.AlbumArtRepository
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.MusicImportRepository
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository as DomainPlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository as DomainPlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistResumeRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import cn.com.dcsgo.mihx.domain.repository.SongMetadataRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideMusicRepository(
        @ApplicationContext context: Context,
        legacyJsonMigration: LegacyJsonMigration,
        melodyDao: MelodyDao,
    ): MusicRepository = MusicRepository(context, legacyJsonMigration, melodyDao)

    @Provides
    @Singleton
    fun providePlayStatsRepository(
        @ApplicationContext context: Context,
        melodyDao: MelodyDao,
    ): PlayStatsRepository = PlayStatsRepository(context, melodyDao)

    @Provides
    @Singleton
    fun provideQuickSkipSongsRepository(
        @ApplicationContext context: Context,
        melodyDao: MelodyDao,
    ): QuickSkipSongsRepository = QuickSkipSongsRepository(context, melodyDao)

    @Provides
    @Singleton
    fun providePlayerSettingsRepository(
        @PlayerSettingsStore settingsStore: DataStore<Preferences>,
        @LegacyMusicPlayerPreferences legacyPrefs: android.content.SharedPreferences,
    ): PlayerSettingsRepository = PlayerSettingsRepository(settingsStore, legacyPrefs)

    @Provides
    @Singleton
    fun providePlaylistResumeRepository(
        @ApplicationContext context: Context,
    ): PlaylistResumeRepository = PlaylistResumeDataStore(context)

    @Provides
    @Singleton
    fun provideMediaMetadataRepository(@ApplicationContext context: Context): MediaMetadataRepository =
        MediaMetadataRepository(context)

    @Provides
    fun provideSongRepository(repository: SongRepositoryAdapter): SongRepository = repository

    @Provides
    fun providePlaylistRepository(repository: PlaylistRepositoryAdapter): PlaylistRepository = repository

    @Provides
    fun provideMusicImportRepository(repository: MusicImportRepositoryAdapter): MusicImportRepository = repository

    @Provides
    fun provideAlbumArtRepository(repository: AlbumArtRepositoryAdapter): AlbumArtRepository = repository

    @Provides
    fun provideLyricsRepository(repository: MediaMetadataRepository): LyricsRepository = repository

    @Provides
    fun provideSongMetadataRepository(repository: MediaMetadataRepository): SongMetadataRepository = repository

    @Provides
    fun provideDomainPlayStatsRepository(repository: PlayStatsRepository): DomainPlayStatsRepository = repository

    @Provides
    fun provideQuickSkipRepository(repository: QuickSkipSongsRepository): QuickSkipRepository = repository

    @Provides
    fun provideDomainPlayerSettingsRepository(
        repository: PlayerSettingsRepository,
    ): DomainPlayerSettingsRepository = repository
}
