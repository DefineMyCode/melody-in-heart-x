package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.AppLogger
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.AlbumEntry
import cn.com.dcsgo.mihx.core.model.ArtistEntry
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.core.model.ScreenOrientationMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.core.model.ThemeMode
import cn.com.dcsgo.mihx.core.model.ThemeVariant
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.importing.ImportResult
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.DuplicateSongGroup
import cn.com.dcsgo.mihx.domain.model.FileCheckMode
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.model.SongDeletionActions
import cn.com.dcsgo.mihx.domain.model.SongDeletionPlan
import cn.com.dcsgo.mihx.domain.model.SongSortMode
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitor
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackSnapshot
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlan
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort
import cn.com.dcsgo.mihx.domain.playback.ControllerQueueSnapshot
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPort
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPortFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitor
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackQueueActionPlanner
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorage
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServices
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServicesFactory
import cn.com.dcsgo.mihx.domain.playback.QueueManager
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState
import cn.com.dcsgo.mihx.domain.playback.SongGroupCoordinator
import cn.com.dcsgo.mihx.domain.playlist.PlaylistActions
import cn.com.dcsgo.mihx.domain.playlist.PlaylistSnapshot
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions
import cn.com.dcsgo.mihx.domain.repository.AlbumArtRepository
import cn.com.dcsgo.mihx.domain.repository.EmotionTableStat
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaybackStatsSnapshot
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository
import cn.com.dcsgo.mihx.domain.repository.WordPreferenceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 [PlayerRuntime] 的 m-11 修复：`playRelatedSongs` 使用注入的 [Random] 实例
 * 进行洗牌，而非直接依赖 `Random.Default`，使随机顺序可注入、可测试。
 *
 * 用 Dispatchers.Unconfined 让 graph 内部的 launch/withContext 同步执行；
 * `PlayerLibraryFacade.loadInitialData` 内部用 `withContext(Dispatchers.IO)`，
 * 由 `withTimeout` 轮询等待曲库加载完成后再调用 `playRelatedSongs`。
 */
class PlayerRuntimeTest {

    /** 当前歌曲 Song1：关联 Song2（同专辑 AlbumX）、Song3（同歌手 ArtistA），Song4 无关 */
    private val testSongs = listOf(
        Song(id = 1, title = "Song1", artist = "ArtistA", album = "AlbumX"),
        Song(id = 2, title = "Song2", artist = "ArtistB", album = "AlbumX"),
        Song(id = 3, title = "Song3", artist = "ArtistA", album = "AlbumY"),
        Song(id = 4, title = "Song4", artist = "ArtistC", album = "AlbumZ"),
    )

    private fun createRuntime(random: Random): PlayerRuntime {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dispatchers = CoroutineDispatchers(
            main = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
        )
        val songRepository = FakeSongRepository(testSongs)

        return PlayerRuntime(
            scope = scope,
            songRepository = songRepository,
            albumArtRepository = FakeAlbumArtRepository(),
            playStatsRepository = FakePlayStatsRepository(),
            playerSettingsRepository = FakePlayerSettingsRepository(),
            controllerQueuePlanner = FakeControllerQueuePlanner(),
            playbackControllerPortFactory = PlaybackControllerPortFactory { FakePlaybackControllerPort() },
            playbackDurationMonitorFactory = PlaybackDurationMonitorFactory { FakePlaybackDurationMonitor() },
            playbackStateStorageFactory = PlaybackStateStorageFactory { FakePlaybackStateStorage() },
            bluetoothPlaybackMonitorFactory = BluetoothPlaybackMonitorFactory { _, _ -> FakeBluetoothPlaybackMonitor() },
            playerQueueServicesFactory = PlayerQueueServicesFactory {
                PlayerQueueServices(
                    importer = FakeFolderImporter(),
                    playlistActions = FakePlaylistActions(testSongs),
                    songGroupCoordinator = SongGroupCoordinator(
                        updateSongTitleOverride = { _, _ -> false },
                        getSongs = { testSongs },
                    ),
                    songDeletionActions = FakeSongDeletionActions(),
                    quickSkipActions = FakeQuickSkipActions(),
                    playOrderBuilder = QueueManager.defaultPlayOrderBuilder,
                    queueActionPlanner = PlaybackQueueActionPlanner(),
                )
            },
            dispatchers = dispatchers,
            timeSlotConfigRepository = FakeTimeSlotConfigRepository(),
            songEmotionRepository = FakeSongEmotionRepository(),
            wordPreferenceRepository = FakeWordPreferenceRepository(),
            random = random,
        )
    }

    @Test
    fun playRelatedSongsUsesInjectedRandom() = runBlocking {
        // 安装 no-op logger，避免 JVM 单测撞上未 mock 的 android.util.Log
        AppLog.install(NoOpLogger)

        val random = Random(42)
        val runtime = createRuntime(random)

        try {
            // 启动：加载曲库到 _uiState.songs
            runtime.start()

            // 等待曲库加载完成（start() 内部 withContext(Dispatchers.IO) 挂起后由后台恢复）
            val startWait = System.currentTimeMillis()
            while (runtime.uiState.value.songs.isEmpty() &&
                System.currentTimeMillis() - startWait < 5_000
            ) {
                delay(10)
            }

            // 当前歌曲 Song1：关联 Song2（同专辑）、Song3（同歌手），Song4 无关
            val currentSong = testSongs[0]
            val related = listOf(testSongs[1], testSongs[2])

            // 期望队列：当前歌曲 ID + 注入 Random 洗牌后的关联歌曲 ID
            val expectedQueueIds = listOf(currentSong.id) + related.shuffled(random).map { it.id }

            val added = runtime.playRelatedSongs(currentSong)
            assertEquals(2, added)

            val actualQueueIds = runtime.uiState.value.playQueue.songs.map { it.id }
            assertEquals(expectedQueueIds, actualQueueIds)
        } finally {
            runtime.onCleared()
        }
    }

    @Test
    fun playRelatedSongsWithDifferentRandomSeedsProducesDifferentOrder() = runBlocking {
        AppLog.install(NoOpLogger)

        // 两组不同的种子，验证洗牌结果跟随注入的 Random
        val runtimeA = createRuntime(Random(42))
        val runtimeB = createRuntime(Random(999))

        try {
            for (runtime in listOf(runtimeA, runtimeB)) {
                runtime.start()
                val startWait = System.currentTimeMillis()
                while (runtime.uiState.value.songs.isEmpty() &&
                    System.currentTimeMillis() - startWait < 5_000
                ) {
                    delay(10)
                }
            }

            val currentSong = testSongs[0]
            val related = listOf(testSongs[1], testSongs[2])

            val orderA = listOf(currentSong.id) + related.shuffled(Random(42)).map { it.id }
            val orderB = listOf(currentSong.id) + related.shuffled(Random(999)).map { it.id }

            runtimeA.playRelatedSongs(currentSong)
            runtimeB.playRelatedSongs(currentSong)

            assertEquals(orderA, runtimeA.uiState.value.playQueue.songs.map { it.id })
            assertEquals(orderB, runtimeB.uiState.value.playQueue.songs.map { it.id })
        } finally {
            runtimeA.onCleared()
            runtimeB.onCleared()
        }
    }

    // ── Fakes ──

    private object NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String, throwable: Throwable?) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
        override fun performance(tag: String, message: String) {}
    }

    private class FakeSongRepository(private val songs: List<Song>) : SongRepository {
        override suspend fun loadSongs(): List<Song> = songs
        override suspend fun countSongs(): Int = songs.size
        override fun observeSongsSnapshot(): List<Song> = songs
        override fun setSongsChangedListener(listener: (() -> Unit)?) {}
        override suspend fun containsSong(songId: Int): Boolean = songs.any { it.id == songId }
        override suspend fun loadLibraryArtists(): List<ArtistEntry> = emptyList()
        override suspend fun loadLibraryAlbums(): List<AlbumEntry> = emptyList()
        override fun updateSongTitleOverride(songId: Int, titleOverride: String?): Boolean = false
        override suspend fun deleteSong(songId: Int): DeleteSongResult =
            DeleteSongResult.Failure("not supported in test")
        override suspend fun validateAndCleanupLocalFiles(): LocalFileValidationResult =
            throw UnsupportedOperationException()
        override suspend fun validateAndCleanupLocalFiles(mode: FileCheckMode): LocalFileValidationResult =
            throw UnsupportedOperationException()
        override suspend fun scanDuplicateSongGroups(): List<DuplicateSongGroup> = emptyList()
        override suspend fun deduplicateSongs(groups: List<DuplicateSongGroup>): List<Int> = emptyList()
    }

    private class FakePlaylistRepository : PlaylistRepository {
        override fun getPlaylists(): List<Playlist> = emptyList()
        override fun createPlaylist(name: String): Playlist? = null
        override fun deletePlaylist(playlistId: Int): Boolean = false
        override fun renamePlaylist(playlistId: Int, newName: String): Boolean = false
        override fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean = false
        override fun removeSongFromPlaylist(playlistId: Int, songId: Int): Boolean = false
        override fun replacePlaylistSongs(playlistId: Int, songIds: List<Int>): Boolean = false
        override fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>): Boolean = false
        override fun getSongsByPlaylistId(playlistId: Int): List<Song> = emptyList()
        override fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean = false
    }

    private class FakeAlbumArtRepository : AlbumArtRepository {
        override suspend fun refreshAllAlbumArt() {}
    }

    private class FakePlayStatsRepository : PlayStatsRepository {
        override suspend fun getCounts(songIds: List<Int>): Map<Int, Int> = emptyMap()
        override suspend fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int> = emptyMap()
        override suspend fun getRankedCounts(
            useRawCounts: Boolean,
            descending: Boolean,
        ): List<Pair<Int, Int>> = emptyList()
        override suspend fun getPlayCountsForSort(): Map<Int, Int> = emptyMap()
        override suspend fun getLastPlayedAtForSort(): Map<Int, Long> = emptyMap()
        override suspend fun increment(songId: Int): Int = 0
        override suspend fun incrementRawPlayCount(songId: Int): Int = 0
        override suspend fun recordCompletedPlay(songId: Int) {}
        override suspend fun recordPlaybackSession(
            songId: Int,
            startedAtMs: Long,
            durationMs: Long,
            isEffectivePlay: Boolean,
        ) {
        }

        override suspend fun playbackStatsSnapshot(): PlaybackStatsSnapshot = PlaybackStatsSnapshot(
            todayDurationMs = 0L,
            todaySongCount = 0,
            yesterdayDurationMs = 0L,
            lastWeekSameDayDurationMs = 0L,
            weekTotalMs = 0L,
            lastWeekTotalMs = 0L,
            weekDays = emptyList(),
            weeklyTop = emptyList(),
            monthlyTop = emptyList(),
        )
    }

    private class FakePlayerSettingsRepository : PlayerSettingsRepository {
        override val themeMode: Flow<ThemeMode> = flowOf(ThemeMode.entries.first())
        override val themeVariant: Flow<ThemeVariant> = flowOf(ThemeVariant.entries.first())
        override val screenOrientationMode: Flow<ScreenOrientationMode> =
            flowOf(ScreenOrientationMode.entries.first())
        override val globalUniformRandomEnabled: Flow<Boolean> = flowOf(false)
        override val bluetoothPlaybackMonitoringEnabled: Flow<Boolean> = flowOf(false)
        override val playbackNotificationEnabled: Flow<Boolean> = flowOf(false)
        override val lyricFontScale: Flow<Float> = flowOf(1.0f)
        override val dailyListeningGoalMinutes: Flow<Int> = flowOf(0)
        override suspend fun setThemeMode(mode: ThemeMode) {}
        override suspend fun setThemeVariant(variant: ThemeVariant) {}
        override suspend fun setScreenOrientationMode(mode: ScreenOrientationMode) {}
        override suspend fun setGlobalUniformRandomEnabled(enabled: Boolean) {}
        override suspend fun setBluetoothPlaybackMonitoringEnabled(enabled: Boolean) {}
        override suspend fun setPlaybackNotificationEnabled(enabled: Boolean) {}
        override suspend fun setLyricFontScale(scale: Float) {}
        override suspend fun setDailyListeningGoalMinutes(minutes: Int) {}
        override val sleepTimerEndAtMs: Flow<Long> = flowOf(0L)
        override val sleepTimerPlayLastSong: Flow<Boolean> = flowOf(false)
        override suspend fun setSleepTimerEndAtMs(endAtMs: Long) {}
        override suspend fun setSleepTimerPlayLastSong(enabled: Boolean) {}
        override val emotionScanPaused: Flow<Boolean> = flowOf(false)
        override suspend fun setEmotionScanPaused(paused: Boolean) {}
        override val moodTimeSlotEnabled: Flow<Boolean> = flowOf(false)
        override suspend fun setMoodTimeSlotEnabled(enabled: Boolean) {}
        override val randomEnhanceMode: Flow<RandomEnhanceMode> = flowOf(RandomEnhanceMode.OFF)
        override suspend fun setRandomEnhanceMode(mode: RandomEnhanceMode) {}
        override val songSortMode: Flow<SongSortMode> = flowOf(SongSortMode.IMPORT_ORDER)
        override suspend fun setSongSortMode(mode: SongSortMode) {}
        override val songSortAscending: Flow<Boolean> = flowOf(true)
        override suspend fun setSongSortAscending(ascending: Boolean) {}
    }

    private class FakeControllerQueuePlanner : ControllerQueuePlannerPort {
        override fun plan(queue: PlayQueue, requestedIndex: Int): ControllerQueuePlan? = null
    }

    private class FakePlaybackControllerPort : PlaybackControllerPort {
        override val hasCurrentMediaItem: Boolean = false
        override val isPlaying: Boolean? = null
        override fun playQueue(plan: ControllerQueuePlan) {}
        override fun prepareQueue(plan: ControllerQueuePlan, positionMs: Long) {}
        override fun playSingle(song: Song): Boolean = false
        override fun pause() {}
        override fun play() {}
        override fun seekTo(positionMs: Long) {}
        override fun currentPositionMs(fallback: Long): Long = fallback
        override fun durationMs(fallback: Long): Long = fallback
        override fun startService() {}
        override fun connect(onConnected: (ControllerPlaybackSnapshot) -> Unit) {}
        override fun snapshot(): ControllerPlaybackSnapshot? = null
        override fun queueInfo(): ControllerQueueSnapshot? = null
        override fun clearPlaylist() {}
        override fun playPrevious() {}
        override fun playNext() {}
        override fun syncQueue(plan: ControllerQueuePlan) {}
        override fun release() {}
    }

    private class FakePlaybackDurationMonitor : PlaybackDurationMonitor {
        override fun startPlayback(songId: Int, durationMs: Long, initialPlayedMs: Long) {}
        override fun pausePlayback() {}
        override fun resumePlayback() {}
        override fun startSeeking() {}
        override fun endSeeking() {}
        override fun updateDuration(songId: Int, durationMs: Long) {}
        override fun stopPlayback() {}
        override fun release() {}
        override fun currentPlayedMs(): Long = 0L
    }

    private class FakePlaybackStateStorage : PlaybackStateStorage {
        override suspend fun save(
            queue: PlayQueue,
            positionMs: Long,
            isInfinitePlay: Boolean,
            infinitePlayedSongIds: Set<Int>,
            isExplorePlay: Boolean,
            currentSongId: Int?,
        ) {
        }

        override suspend fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long) {}
        override suspend fun clear() {}
        override suspend fun restore(allSongs: List<Song>): RestoredPlaybackState? = null
    }

    private class FakeBluetoothPlaybackMonitor : BluetoothPlaybackMonitor {
        override fun initialize() {}
        override fun release() {}
    }

    private class FakeFolderImporter : FolderImporter {
        override suspend fun importFolder(
            treeUri: android.net.Uri,
            onProgress: (processed: Int, total: Int) -> Unit,
        ): ImportResult = ImportResult(0, emptyList(), emptyList())
    }

    private class FakePlaylistActions(private val songs: List<Song>) : PlaylistActions {
        override fun getSongsByPlaylist(playlist: Playlist, songs: List<Song>): List<Song> = emptyList()
        override fun createPlaylist(name: String): Playlist? = null
        override fun deletePlaylist(playlistId: Int) {}
        override fun renamePlaylist(playlistId: Int, newName: String): Boolean = false
        override fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean = false
        override fun removeSongFromPlaylist(playlistId: Int, songId: Int) {}
        override fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>) {}
        override fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean = false
        override fun snapshot(): PlaylistSnapshot = PlaylistSnapshot(songs = songs, playlists = emptyList())
    }

    private class FakeSongDeletionActions : SongDeletionActions {
        override suspend fun delete(songId: Int): SongDeletionPlan =
            throw UnsupportedOperationException()
    }

    private class FakeQuickSkipActions : QuickSkipActions {
        override suspend fun getSongs(): List<Song> = emptyList()
        override suspend fun add(songId: Int): Boolean = false
        override suspend fun remove(songId: Int): Boolean = false
        override suspend fun contains(songId: Int): Boolean = false
        override suspend fun syncToPlaylist() {}
    }

    private class FakeQuickSkipRepository : QuickSkipRepository {
        override suspend fun getSongIds(): Set<Int> = emptySet()
        override suspend fun add(songId: Int): Boolean = false
        override suspend fun remove(songId: Int): Boolean = false
        override suspend fun contains(songId: Int): Boolean = false
        override suspend fun incrementShortPlayCount(songId: Int): Int = 0
        override suspend fun resetShortPlayCount(songId: Int) {}
    }

    private class FakeTimeSlotConfigRepository : TimeSlotConfigRepository {
        override fun observeConfigs(): Flow<List<TimeSlotConfig>> = flowOf(emptyList())
        override suspend fun currentConfigs(): List<TimeSlotConfig> = emptyList()
        override suspend fun save(config: TimeSlotConfig) {}
        override suspend fun delete(id: Long) {}
    }

    private class FakeSongEmotionRepository : SongEmotionRepository {
        override suspend fun get(songId: Int): SongEmotion? = null
        override suspend fun getAll(): Map<Int, SongEmotion> = emptyMap()
        override fun observeAll(): Flow<Map<Int, SongEmotion>> = flowOf(emptyMap())
        override suspend fun tableStat(): EmotionTableStat = EmotionTableStat(0, 0L)
        override suspend fun recentAnalyzedTimestamps(limit: Int): List<Long> = emptyList()
        override suspend fun correctionCount(): Int = 0
        override suspend fun analyzedVersions(): Map<Int, String> = emptyMap()
        override suspend fun songIdsMissingVocalScore(): Set<Int> = emptySet()
        override suspend fun upsert(emotion: SongEmotion) {}
        override suspend fun saveCorrection(songId: Int, tags: List<String>, edited: Boolean?) {}
        override suspend fun clearCorrection(songId: Int) {}
        override suspend fun delete(songId: Int) {}
    }

    private class FakeWordPreferenceRepository : WordPreferenceRepository {
        override fun observeAll(): Flow<Map<String, WordPreference>> = flowOf(emptyMap())
        override suspend fun getAll(): Map<String, WordPreference> = emptyMap()
        override suspend fun recordSongOutcome(votes: Map<String, Int>, liked: Boolean) {}
        override suspend fun clearAll() {}
    }
}
