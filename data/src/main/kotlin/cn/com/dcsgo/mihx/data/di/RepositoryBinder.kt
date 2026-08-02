package cn.com.dcsgo.mihx.data.di

import cn.com.dcsgo.mihx.data.LibraryImporterImpl
import cn.com.dcsgo.mihx.data.repository.PlayStatsRepositoryImpl
import cn.com.dcsgo.mihx.data.repository.PlaybackStateRepositoryImpl
import cn.com.dcsgo.mihx.data.repository.PlayerSettingsRepositoryImpl
import cn.com.dcsgo.mihx.data.repository.PlaylistRepositoryImpl
import cn.com.dcsgo.mihx.data.repository.SongRepositoryImpl
import cn.com.dcsgo.mihx.domain.repository.LibraryImporter
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the five Phase 4 repository implementations to their [cn.com.dcsgo.mihx.domain.repository]
 * interfaces (plan P4-2). Architecture gate A2/A3 keeps `:feature:*` depending only on `:domain`
 * interfaces, never on `:data`. The two DataStores are `@Inject`-constructed `@Singleton`s, so
 * Hilt supplies them without an explicit `@Provides` here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBinder {
    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    @Singleton
    abstract fun bindLibraryImporter(impl: LibraryImporterImpl): LibraryImporter

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackStateRepository(impl: PlaybackStateRepositoryImpl): PlaybackStateRepository

    @Binds
    @Singleton
    abstract fun bindPlayerSettingsRepository(impl: PlayerSettingsRepositoryImpl): PlayerSettingsRepository

    @Binds
    @Singleton
    abstract fun bindPlayStatsRepository(impl: PlayStatsRepositoryImpl): PlayStatsRepository
}
