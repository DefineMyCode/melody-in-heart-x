package cn.com.dcsgo.mihx.app.di

import android.content.Context
import cn.com.dcsgo.mihx.data.player.BluetoothAudioQualityManager
import cn.com.dcsgo.mihx.data.player.BluetoothPlaybackCoordinator
import cn.com.dcsgo.mihx.data.player.BluetoothStateManager
import cn.com.dcsgo.mihx.data.player.ImportCoordinator
import cn.com.dcsgo.mihx.data.player.PlayDurationTracker
import cn.com.dcsgo.mihx.data.player.PlaybackController
import cn.com.dcsgo.mihx.data.player.PlaybackStateStore
import cn.com.dcsgo.mihx.data.player.PlaylistManager
import cn.com.dcsgo.mihx.data.player.QuickSkipCoordinator
import cn.com.dcsgo.mihx.data.player.SongDeletionCoordinator
import cn.com.dcsgo.mihx.data.player.AppMediaSessionService
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPortFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackQueueActionPlanner
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServices
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServicesFactory
import cn.com.dcsgo.mihx.domain.playback.QueueManager
import cn.com.dcsgo.mihx.domain.playback.SongGroupCoordinator
import cn.com.dcsgo.mihx.domain.repository.MusicImportRepository
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.player.window.WindowedControllerQueuePlanner
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    fun provideControllerQueuePlanner(): ControllerQueuePlannerPort = WindowedControllerQueuePlanner()

    @Provides
    fun providePlaybackDurationMonitorFactory(
        playStatsRepository: PlayStatsRepository,
        quickSkipRepository: QuickSkipRepository,
    ): PlaybackDurationMonitorFactory {
        return PlaybackDurationMonitorFactory {
            PlayDurationTracker(playStatsRepository, quickSkipRepository)
        }
    }

    @Provides
    @Singleton
    fun providePlaybackStateStore(
        @ApplicationContext context: Context,
    ): PlaybackStateStore = PlaybackStateStore(context)

    @Provides
    fun providePlaybackStateStorageFactory(
        playbackStateStore: PlaybackStateStore,
    ): PlaybackStateStorageFactory {
        return PlaybackStateStorageFactory { playbackStateStore }
    }

    @Provides
    fun providePlaybackStateRepository(
        playbackStateStore: PlaybackStateStore,
    ): PlaybackStateRepository = playbackStateStore

    @Provides
    fun provideBluetoothPlaybackMonitorFactory(
        @ApplicationContext context: Context,
    ): BluetoothPlaybackMonitorFactory {
        return BluetoothPlaybackMonitorFactory { isPlaying, pausePlayback ->
            BluetoothPlaybackCoordinator(
                bluetoothStateManager = BluetoothStateManager(context),
                audioQualityManager = BluetoothAudioQualityManager(context),
                isPlaying = isPlaying,
                pausePlayback = pausePlayback,
            )
        }
    }

    @Provides
    fun providePlaybackControllerPortFactory(
        @ApplicationContext context: Context,
    ): PlaybackControllerPortFactory {
        return PlaybackControllerPortFactory { callbacks ->
            PlaybackController(
                context = context,
                serviceClass = AppMediaSessionService::class.java,
                callbacks = callbacks,
            )
        }
    }

    @Provides
    fun providePlayerQueueServicesFactory(
        songRepository: SongRepository,
        playlistRepository: PlaylistRepository,
        musicImportRepository: MusicImportRepository,
        playStatsRepository: PlayStatsRepository,
        quickSkipRepository: QuickSkipRepository,
    ): PlayerQueueServicesFactory {
        return PlayerQueueServicesFactory {
            // 随机播放模式 = 纯乱序，与全局均匀随机无关
            val playOrderBuilder = QueueManager.defaultPlayOrderBuilder
            PlayerQueueServices(
                importer = ImportCoordinator(
                    importRepository = musicImportRepository,
                    songRepository = songRepository,
                    playlistRepository = playlistRepository,
                ),
                playlistActions = PlaylistManager(
                    songRepository = songRepository,
                    playlistRepository = playlistRepository,
                ),
                songGroupCoordinator = SongGroupCoordinator(
                    updateSongTitleOverride = songRepository::updateSongTitleOverride,
                    getSongs = songRepository::observeSongsSnapshot,
                ),
                songDeletionActions = SongDeletionCoordinator(songRepository::deleteSong),
                quickSkipActions = QuickSkipCoordinator(
                    songRepository = songRepository,
                    playlistRepository = playlistRepository,
                    quickSkipRepository = quickSkipRepository,
                ),
                playOrderBuilder = playOrderBuilder,
                queueActionPlanner = PlaybackQueueActionPlanner(playOrderBuilder),
            )
        }
    }
}
