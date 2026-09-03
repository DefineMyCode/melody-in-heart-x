package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitorFactory
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.playback.ControllerQueuePlannerPort
import cn.com.dcsgo.mihx.domain.playback.PlaybackControllerPortFactory
import cn.com.dcsgo.mihx.domain.playback.PlaybackStateStorageFactory
import cn.com.dcsgo.mihx.domain.playback.PlayerQueueServicesFactory
import cn.com.dcsgo.mihx.domain.repository.AlbumArtRepository
import cn.com.dcsgo.mihx.domain.repository.PlayStatsRepository
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "PlayerViewModel"

class PlayerRuntimeFactory @Inject constructor(
    private val songRepository: SongRepository,
    private val albumArtRepository: AlbumArtRepository,
    private val playStatsRepository: PlayStatsRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val controllerQueuePlanner: ControllerQueuePlannerPort,
    private val playbackControllerPortFactory: PlaybackControllerPortFactory,
    private val playbackDurationMonitorFactory: PlaybackDurationMonitorFactory,
    private val playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val bluetoothPlaybackMonitorFactory: BluetoothPlaybackMonitorFactory,
    private val playerQueueServicesFactory: PlayerQueueServicesFactory,
    private val dispatchers: CoroutineDispatchers,
    private val timeSlotConfigRepository: cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository,
    private val songEmotionRepository: cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository,
) {
    internal fun create(scope: CoroutineScope): PlayerRuntime {
        return PlayerRuntime(
            scope = scope,
            songRepository = songRepository,
            albumArtRepository = albumArtRepository,
            playStatsRepository = playStatsRepository,
            playerSettingsRepository = playerSettingsRepository,
            controllerQueuePlanner = controllerQueuePlanner,
            playbackControllerPortFactory = playbackControllerPortFactory,
            playbackDurationMonitorFactory = playbackDurationMonitorFactory,
            playbackStateStorageFactory = playbackStateStorageFactory,
            bluetoothPlaybackMonitorFactory = bluetoothPlaybackMonitorFactory,
            playerQueueServicesFactory = playerQueueServicesFactory,
            dispatchers = dispatchers,
            timeSlotConfigRepository = timeSlotConfigRepository,
            songEmotionRepository = songEmotionRepository,
        )
    }
}

internal class PlayerRuntime(
    private val scope: CoroutineScope,
    private val songRepository: SongRepository,
    private val albumArtRepository: AlbumArtRepository,
    val playStatsRepository: PlayStatsRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val controllerQueuePlanner: ControllerQueuePlannerPort,
    private val playbackControllerPortFactory: PlaybackControllerPortFactory,
    private val playbackDurationMonitorFactory: PlaybackDurationMonitorFactory,
    private val playbackStateStorageFactory: PlaybackStateStorageFactory,
    private val bluetoothPlaybackMonitorFactory: BluetoothPlaybackMonitorFactory,
    private val playerQueueServicesFactory: PlayerQueueServicesFactory,
    private val dispatchers: CoroutineDispatchers,
    private val timeSlotConfigRepository: cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository,
    private val songEmotionRepository: cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository,
) {
    private val mediaControllerGraph: PlayerMediaControllerGraph by lazy {
        PlayerMediaControllerGraph(
            playbackControllerPortFactory = playbackControllerPortFactory,
            controllerStateAdapter = controllerStateAdapter,
            handleMediaItemEnded = mediaEventFacade::handleMediaItemEnded,
            handlePlaybackEnded = mediaEventFacade::handlePlaybackEnded,
            handlePlayerError = ::handlePlayerSourceError,
            handleControllerUnavailable = ::handleControllerUnavailable,
        )
    }
    private val playbackController by lazy { mediaControllerGraph.playbackController }
    private val queueGraph = PlayerQueueGraph(
        playerQueueServicesFactory = playerQueueServicesFactory,
    )
    private val importCoordinator get() = queueGraph.importCoordinator
    private val playlistManager get() = queueGraph.playlistManager
    private val songGroupCoordinator get() = queueGraph.songGroupCoordinator
    private val songDeletionCoordinator get() = queueGraph.songDeletionCoordinator
    private val quickSkipCoordinator get() = queueGraph.quickSkipCoordinator
    private val playOrderBuilder get() = queueGraph.playOrderBuilder
    private val controllerStateSynchronizer = ControllerPlaybackStateSynchronizer()

    private var trackedSongId: Int? = null

    private val playDurationTracker = playbackDurationMonitorFactory.create()

    private val bluetoothGraph by lazy {
        PlayerBluetoothGraph(
            bluetoothPlaybackMonitorFactory = bluetoothPlaybackMonitorFactory,
            isPlaying = { _uiState.value.isPlaying },
            pausePlayback = { playbackBridgeFacade.pausePlayback() },
        )
    }

    private val persistenceGraph by lazy {
        PlayerPersistenceGraph(
            playbackStateStorageFactory = playbackStateStorageFactory,
            scope = scope,
            dispatchers = dispatchers,
            state = { _uiState.value },
            updateState = ::updateUiState,
            syncPlaybackState = { mediaControllerGraph.syncCurrentPlaybackState() },
            currentPlaybackPositionMs = { playbackBridgeFacade.currentPlaybackPositionMs() },
            prepareControllerQueue = { queue, index, positionMs ->
                playbackBridgeFacade.prepareControllerQueue(queue, index, positionMs)
            },
            hasLiveSession = { liveSessionActive() },
            log = { message -> AppLog.debug(TAG, message) },
        )
    }

    /**
     * live session 判据：controller 已连接且队列里有媒体项。
     *
     * 不用「播放位置 > 0」猜——restore 的 IO 读取可能先于 MediaController 连接完成
     * （buildAsync 异步跨进程绑定），此时 [PlaybackBridgeFacade.currentPlaybackPositionMs]
     * 会退化成 UI 状态值（重建后恒为 0），把 live session 误判成无会话，进而走完整
     * restore 覆盖正在播放的会话。
     */
    private fun liveSessionActive(): Boolean =
        (mediaControllerGraph.controllerQueueInfo()?.mediaItemCount ?: 0) > 0

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** 本地歌曲文件校验结果（未确认前保留，供结果页重复进入） */
    private val _validationResult = MutableStateFlow<LocalFileValidationResult?>(null)
    val validationResult: StateFlow<LocalFileValidationResult?> = _validationResult.asStateFlow()

    /** 本地歌曲文件校验是否正在后台运行 */
    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    /** 播放位置（毫秒）独立窄流：仅驱动进度条/歌词等需要实时位置的组件，避免整壳重组 */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /**
     * 定时关闭剩余毫秒独立窄流（M-6，评审 2026-09-03）：倒计时每秒 tick 只写这里，
     * 仅由定时关闭 Chip 局部订阅，避免每秒写主 UiState 导致整壳重组。
     */
    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    /**
     * 统一的状态更新入口。当离散事件（seek、切歌、恢复、暂停等）写入
     * [PlayerUiState.currentPositionMs] 时，同步到 [positionMs] 窄流，
     * 保证 UI 立即反映到目标位置。播放期间的逐 tick 更新走 [positionMs] 直写，不经过这里。
     */
    private fun updateUiState(transform: (PlayerUiState) -> PlayerUiState) {
        narrowFlowSync.update(transform)
    }

    /** 窄流同步器：主 UiState 更新 + 离散位置/倒计时事件向窄流的单向传播（可测） */
    private val narrowFlowSync = NarrowFlowSync(
        uiState = _uiState,
        positionMs = _positionMs,
        sleepTimerRemainingMs = _sleepTimerRemainingMs,
    )

    /** 定时关闭取消/到点：倒计时窄流显式归零（M-6 后 uiState 不再承载 tick 值） */
    private fun resetSleepTimerNarrowFlow() {
        narrowFlowSync.resetSleepTimerNarrowFlow()
    }

    /**
     * 播放控制器不可用时的兜底处理（例如待执行操作队列超限被限流丢弃）。
     * 经 [updateUiState] 把原因与丢弃计数写入 [PlayerUiState.errorMessage]，供 UI 提示。
     */
    private fun handleControllerUnavailable(droppedActionCount: Int, reason: String) {
        AppLog.warning(TAG, "Playback controller unavailable: $reason, dropped=$droppedActionCount")
        // 启动期连接失败（controller 视为空）也放行 restore 决策，否则 pendingRestore 会
        // 永久悬挂、UI 队列永不恢复。播放中不可用时 pendingRestore 早已消费，此处无副作用。
        persistenceGraph.onControllerReady()
        val suffix = if (droppedActionCount > 0) "，已取消 $droppedActionCount 个待执行操作" else ""
        updateUiState { it.copy(errorMessage = "$reason$suffix，请稍后重试") }
    }

    val playQueue: PlayQueue get() = _uiState.value.playQueue
    val currentSong: Song? get() = _uiState.value.currentSong
    val currentPlayMode: PlayMode get() = _uiState.value.playQueue.playMode

    private val playbackProgressTicker = PlayerPlaybackProgressTicker(
        scope = scope,
        isPlaying = { _uiState.value.isPlaying },
        currentPositionMs = { playbackBridgeFacade.currentPlaybackPositionMs() },
        updatePosition = { positionMs ->
            // 只写窄流，不再更新 PlayerUiState，避免整壳重组
            _positionMs.value = positionMs
            persistenceGraph.onPlaybackPosition(positionMs)
        },
    )

    private val libraryFacade = PlayerLibraryFacade(
        updateState = ::updateUiState,
        loadPersistedSongs = { songRepository.loadSongs() },
        loadLibraryCatalog = {
            songRepository.loadLibraryArtists() to songRepository.loadLibraryAlbums()
        },
        refreshAllAlbumArt = { onFinished ->
            albumArtRepository.refreshAllAlbumArt()
            onFinished?.invoke()
        },
        snapshot = playlistManager::snapshot,
        setSongsChangedListener = songRepository::setSongsChangedListener,
        catalogScope = scope,
    )
    private val importFacade = PlayerImportFacade(
        updateState = ::updateUiState,
        importer = importCoordinator,
        launch = { task -> scope.launch { task() } },
        logError = { message, error -> AppLog.error("PlayerImportFacade", message, error) },
    )
    private val playlistFacade = PlayerPlaylistFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        playlistManager = playlistManager,
    )
    private val songDeletionFacade = PlayerSongDeletionFacade(
        songDeletionCoordinator = songDeletionCoordinator,
        removeFromPlayQueue = ::removeFromPlayQueue,
        refreshPlaylists = playlistFacade::refresh,
    )
    private val quickSkipFacade = PlayerQuickSkipFacade(
        quickSkipActions = quickSkipCoordinator,
        refreshPlaylists = playlistFacade::refresh,
        launch = { task -> scope.launch { task() } },
    )
    private val controllerQueueFacade = PlayerControllerQueueFacade(
        state = { _uiState.value },
        controllerQueueInfo = { mediaControllerGraph.controllerQueueInfo() },
        clearPlaylist = { playbackController.clearPlaylist() },
        syncQueue = { plan -> playbackController.syncQueue(plan) },
        controllerQueuePlanner = controllerQueuePlanner,
    )
    private val queueFacade = PlayerQueueFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        clearControllerPlaylist = { playbackBridgeFacade.clearControllerPlaylist() },
        clearPlaybackState = ::clearPlaybackState,
        savePlaybackState = ::savePlaybackStateAsync,
        log = { message -> AppLog.debug(TAG, message) },
        planner = queueGraph.queueActionPlanner,
    )
    private val controllerStateFacade = PlayerControllerStateFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        synchronizer = controllerStateSynchronizer,
        trackedSongId = { trackedSongId },
        setTrackedSongId = { trackedSongId = it },
        updateDuration = playDurationTracker::updateDuration,
        startPlayback = playDurationTracker::startPlayback,
        pausePlaybackTracking = playDurationTracker::pausePlayback,
        resumePlaybackTracking = playDurationTracker::resumePlayback,
        savePlaybackState = ::savePlaybackStateAsync,
        onIsPlayingChanged = playbackProgressTicker::updateRunningState,
        onControllerPlaybackSynced = { synced ->
            if (!synced.playQueue.isEmpty &&
                !_uiState.value.isInfinitePlay &&
                playbackBridgeFacade.remainingMediaItems() <= PlayerMediaEventFacade.DEFAULT_REFILL_THRESHOLD
            ) {
                playbackBridgeFacade.syncPlayerQueue(synced.playQueue)
            }
        },
    )
    private val controllerStateAdapter = PlayerControllerStateAdapter(
        syncControllerPlaybackState = controllerStateFacade::syncControllerPlaybackState,
        handleControllerIsPlayingChanged = controllerStateFacade::handleControllerIsPlayingChanged,
    )
    private val playbackFacade = PlayerPlaybackFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        hasCurrentMediaItem = { playbackController.hasCurrentMediaItem },
        setPlayQueue = { songs, startIndex -> setPlayQueue(songs, startIndex) },
        playQueueItem = ::playQueueItem,
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        playSingle = { song -> playbackBridgeFacade.startControllerSinglePlayback(song) },
        pausePlayback = { playbackBridgeFacade.pausePlayback() },
        resumePlayback = { playbackBridgeFacade.resumePlayback() },
        playPreviousInController = { playbackController.playPrevious() },
        playNextInController = { playbackController.playNext() },
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        refillInfinitePlayQueue = { playbackBridgeFacade.refillInfinitePlayQueue() },
        log = { message -> AppLog.debug(TAG, message) },
    )
    private val sleepTimerCoordinator = PlayerSleepTimerCoordinator(
        scope = scope,
        settings = playerSettingsRepository,
        state = { _uiState.value },
        updateState = ::updateUiState,
        updateRemainingMs = { _sleepTimerRemainingMs.value = it },
        resetRemainingMs = ::resetSleepTimerNarrowFlow,
        pausePlayback = { playbackBridgeFacade.pausePlayback() },
    )
    private val mediaEventFacade = PlayerMediaEventFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        stopPlaybackTracking = playDurationTracker::stopPlayback,
        clearTrackedSong = { trackedSongId = null },
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        refillInfinitePlayQueue = { startedSongId, advanceAfterWrap ->
            playbackBridgeFacade.refillInfinitePlayQueue(startedSongId, advanceAfterWrap)
        },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        log = { message -> AppLog.debug(TAG, message) },
        playOrderBuilder = playOrderBuilder,
        onSleepTimerSongEnded = sleepTimerCoordinator::onSongEnded,
    )
    private val versionFacade = PlayerVersionFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        songGroupCoordinator = songGroupCoordinator,
        savePlaybackState = ::savePlaybackStateAsync,
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        playOrderBuilder = playOrderBuilder,
    )
    private val playbackSessionGraph by lazy {
        PlayerPlaybackSessionGraph(
            scope = scope,
            controller = playbackController,
            durationTracker = playDurationTracker,
            state = { _uiState.value },
            updateState = ::updateUiState,
            setTrackedSongId = { trackedSongId = it },
            updateSameNameSongs = versionFacade::updateSameNameSongs,
            savePlaybackState = ::savePlaybackStateAsync,
            onPlaybackStateChanged = playbackProgressTicker::updateRunningState,
            planControllerQueue = controllerQueuePlanner::plan,
        )
    }
    // ── 情境化随心播放（一期）数据源 ──
    // 时段配置量级小，启动时快照 + 配置页变更后主动刷新（refreshMoodSlotCache）；
    // 情绪词条映射同样快照（getAll 全量，随歌曲导入/校准低频变化）。
    @Volatile
    private var moodSlotConfigsCache: List<cn.com.dcsgo.mihx.core.model.TimeSlotConfig> = emptyList()
    @Volatile
    private var moodEmotionTagsCache: Map<Int, List<String>> = emptyMap()

    private fun refreshMoodSlotCache() {
        scope.launch {
            runCatching {
                moodSlotConfigsCache = timeSlotConfigRepository.currentConfigs()
                moodEmotionTagsCache = songEmotionRepository.getAll().mapValues { (_, emotion) ->
                    cn.com.dcsgo.mihx.core.model.emotionTagsOf(emotion)
                }
            }.onFailure { AppLog.warning(TAG, "refreshMoodSlotCache failed: ${it.message}") }
        }
    }

    /** 供配置页保存/删除后立即刷新缓存（否则判定仍用旧配置直到下次冷启动） */
    fun refreshMoodSlots() {
        refreshMoodSlotCache()
    }

    private val randomQueueFacade = PlayerRandomQueueFacade(
        state = { _uiState.value },
        updateState = ::updateUiState,
        setPlayQueue = { songs, startIndex, mode -> setPlayQueue(songs, startIndex, mode) },
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        playFromQueue = { queue, index -> playbackBridgeFacade.playFromQueue(queue, index) },
        rawPlayCounts = playStatsRepository::getRawPlayCounts,
        log = { message -> AppLog.debug(TAG, message) },
        moodSlotConfigs = { moodSlotConfigsCache },
        moodSlotEnabled = { playerSettingsRepository.currentMoodTimeSlotEnabled() },
        moodEmotionTagsOf = { song -> moodEmotionTagsCache[song.id].orEmpty() },
    )
    private val lifecycleFacade = PlayerLifecycleFacade(
        startMediaSessionService = { mediaControllerGraph.startService() },
        syncControllerPlaybackState = { mediaControllerGraph.syncCurrentPlaybackState() },
        savePlaybackState = ::savePlaybackState,
        releasePlaybackController = { mediaControllerGraph.release() },
        releaseBluetoothPlayback = bluetoothGraph::release,
        releasePlayDurationTracker = playDurationTracker::release,
        stopPlaybackProgressTicker = playbackProgressTicker::stop,
        logInfo = { message -> mediaControllerGraph.logInfo(message) },
        logError = { message, error -> mediaControllerGraph.logError(message, error) },
    )
    private val errorFacade = PlayerErrorFacade(
        updateState = ::updateUiState,
        startQueuePlayback = playbackSessionGraph::startQueuePlayback,
    )
    private val playbackBridgeFacade: PlayerPlaybackBridgeFacade by lazy {        PlayerPlaybackBridgeFacade(
            remainingMediaItems = controllerQueueFacade::remainingMediaItems,
            pausePlayback = playbackSessionGraph::pausePlayback,
            resumePlayback = playbackSessionGraph::resumePlayback,
            currentPlaybackPositionMs = playbackSessionGraph::currentPlaybackPositionMs,
            clearControllerPlaylist = controllerQueueFacade::clearControllerPlaylist,
            startControllerQueuePlayback = playbackSessionGraph::startQueuePlayback,
            prepareControllerQueue = playbackSessionGraph::prepareQueue,
            startControllerSinglePlayback = playbackSessionGraph::startSinglePlayback,
            playFromQueue = errorFacade::playFromQueue,
            syncPlayerQueue = controllerQueueFacade::syncPlayerQueue,
            refillInfinitePlayQueue = { startedSongId, advanceAfterWrap ->
                randomQueueFacade.refillInfinitePlayQueue(startedSongId, advanceAfterWrap)
            },
        )
    }
    private val startupFacade = PlayerStartupFacade(
        startService = lifecycleFacade::startService,
        connectMediaController = {
            mediaControllerGraph.connect(
                onConnected = { persistenceGraph.onControllerReady() }
            )
        },
        loadInitialData = ::loadInitialData,
        listenForSongChanges = libraryFacade::listenForSongChanges,
        restorePlaybackState = ::restorePlaybackState,
    )

    fun start() {
        // 启动时把 4 个 DataStore 设置读取 + 定时器恢复（共 6-7 次 runBlocking）移到 IO，
        // 不再阻塞主线程的首帧。startupFacade.start() 仍在主线程执行。
        scope.launch {
            val startupSettings = withContext(dispatchers.io) {
                StartupSettings(
                    globalUniformRandomEnabled = playerSettingsRepository.currentGlobalUniformRandomEnabled(),
                    bluetoothPlaybackMonitoringEnabled = playerSettingsRepository.currentBluetoothPlaybackMonitoringEnabled(),
                    playbackNotificationEnabled = playerSettingsRepository.currentPlaybackNotificationEnabled(),
                    dailyListeningGoalMinutes = playerSettingsRepository.currentDailyListeningGoalMinutes(),
                )
            }
            _uiState.update {
                it.copy(
                    globalUniformRandomEnabled = startupSettings.globalUniformRandomEnabled,
                    bluetoothPlaybackMonitoringEnabled = startupSettings.bluetoothPlaybackMonitoringEnabled,
                    playbackNotificationEnabled = startupSettings.playbackNotificationEnabled,
                    dailyListeningGoalMinutes = startupSettings.dailyListeningGoalMinutes,
                )
            }
            // 定时器恢复（2 读 + 可能 1 写）也放 IO；updateState 与 startTicker 均线程安全
            withContext(dispatchers.io) {
                sleepTimerCoordinator.restore()
            }
            startupFacade.start()
            // 情境化随心播放：启动后异步拉取时段配置与情绪词条快照（低频，不阻塞启动）
            refreshMoodSlotCache()
            if (startupSettings.bluetoothPlaybackMonitoringEnabled) {
                bluetoothGraph.initialize()
            }
        }
    }

    fun getQuickSkipSongs(): List<Song> {
        return quickSkipFacade.getQuickSkipSongs()
    }

    fun addToQuickSkipSongs(songId: Int) {
        quickSkipFacade.addToQuickSkipSongs(songId)
    }

    fun removeFromQuickSkipSongs(songId: Int) {
        quickSkipFacade.removeFromQuickSkipSongs(songId)
    }

    fun isInQuickSkipSongs(songId: Int): Boolean {
        return quickSkipFacade.isInQuickSkipSongs(songId)
    }

    fun syncQuickSkipSongsToPlaylist() {
        quickSkipFacade.syncQuickSkipSongsToPlaylist()
    }

    fun switchToVersion(targetSong: Song) {
        versionFacade.switchToVersion(targetSong)
    }

    fun getGroupedSongs(songs: List<Song>): List<List<Song>> {
        return versionFacade.getGroupedSongs(songs)
    }

    fun getSongsWithSameName(song: Song, songs: List<Song>): List<Song> {
        return versionFacade.getSongsWithSameName(song, songs)
    }

    fun setPlayQueue(
        songs: List<Song>,
        startIndex: Int = 0,
        mode: PlayMode? = null,
        exitInfinitePlay: Boolean = true,
    ) {
        queueFacade.setPlayQueue(songs, startIndex, mode, exitInfinitePlay)
    }

    /**
     * 关联播放：清空播放队列，仅保留当前歌曲，再把其关联的专辑歌曲 + 关联歌手的所有歌曲，
     * 去重后随机追加到队列。不改变无限随机播放状态。
     *
     * @return 实际追加的关联歌曲数量
     */
    fun playRelatedSongs(currentSong: Song): Int {
        val allSongs = _uiState.value.songs
        val related = allSongs
            .filter { it.id != currentSong.id }
            .filter { song ->
                val sameAlbum = currentSong.album.isNotBlank() && song.album == currentSong.album
                val sharedArtist = song.parsedArtists.any { artist -> artist in currentSong.parsedArtists }
                sameAlbum || sharedArtist
            }
            .distinctBy { it.id }
            .shuffled()

        setPlayQueue(
            songs = listOf(currentSong) + related,
            startIndex = 0,
            mode = PlayMode.SEQUENTIAL,
            // 不退出无限随机播放
            exitInfinitePlay = false,
        )
        // 若处于无限随机播放，同步新队列的已播放记录，保证后续补充从队列外歌曲开始
        if (_uiState.value.isInfinitePlay) {
            _uiState.update {
                it.copy(
                    infinitePlayedSongIds = (listOf(currentSong) + related)
                        .map { song -> song.id }
                        .toSet(),
                )
            }
        }
        return related.size
    }

    fun addToPlayQueue(song: Song): Boolean {
        return queueFacade.addToPlayQueue(song)
    }

    fun addSongToNextPlay(song: Song) {
        queueFacade.addSongToNextPlay(song)
    }

    fun addSongsToNextPlay(songs: List<Song>): Int {
        return queueFacade.addSongsToNextPlay(songs)
    }

    fun addToPlayQueue(songs: List<Song>): Int {
        return queueFacade.addToPlayQueue(songs)
    }

    fun removeFromPlayQueue(songId: Int) {
        queueFacade.removeFromPlayQueue(songId)
    }

    fun removeFromPlayQueueAt(index: Int) {
        queueFacade.removeFromPlayQueueAt(index)
    }

    fun clearPlayQueue() {
        queueFacade.clearPlayQueue()
    }

    fun togglePlayMode() {
        queueFacade.togglePlayMode()
    }

    fun setPlayMode(mode: PlayMode) {
        queueFacade.setPlayMode(mode)
    }

    fun playQueueItem(index: Int): Boolean {
        return queueFacade.playQueueItem(index)
    }

    fun playSong(song: Song) {
        playbackFacade.playSong(song)
    }

    fun playSongFromContext(song: Song, contextSongs: List<Song>) {
        playbackFacade.playSongFromContext(song, contextSongs)
    }

    fun togglePlayPause() {
        playbackFacade.togglePlayPause()
    }

    fun playPrevious() {
        playbackFacade.playPrevious()
    }

    fun playNext() {
        playbackFacade.playNext()
    }

    fun startSeeking() {
        playbackSessionGraph.startSeeking()
    }

    fun endSeeking(positionMs: Long) {
        playbackSessionGraph.endSeeking(positionMs)
    }

    fun seekTo(positionMs: Long) {
        playbackSessionGraph.seekTo(positionMs)
    }

    fun clearError() {
        errorFacade.clearError()
    }

    /** 播放源错误（如本地文件缺失）时给用户可读的提示 */
    private fun handlePlayerSourceError(songId: Int?) {
        val song = _uiState.value.songs.firstOrNull { it.id == songId }
            ?: _uiState.value.playQueue.songs.firstOrNull { it.id == songId }
        val title = song?.title ?: "当前歌曲"
        updateUiState { it.copy(errorMessage = "「$title」的本地文件不存在或无法播放") }
    }

    /**
     * 在后台校验本地歌曲文件有效性：扫描文件缺失的歌曲并清理其关联数据。
     * 不阻塞播放与页面浏览；完成后结果保存在 [validationResult]，直到用户确认。
     */
    fun validateLocalFiles() {
        if (_isValidating.value) return
        _isValidating.value = true
        scope.launch {
            try {
                val result = withContext(dispatchers.io) {
                    songRepository.validateAndCleanupLocalFiles()
                }
                _validationResult.value = result
                AppLog.info(TAG, "validateLocalFiles done: ${result.missingCount} missing")
            } catch (e: Exception) {
                AppLog.error(TAG, "validateLocalFiles failed", e)
            } finally {
                _isValidating.value = false
            }
        }
    }

    /** 用户确认校验结果后清除，入口徽标消失 */
    fun acknowledgeValidationResult() {
        _validationResult.value = null
    }

    fun importFolder(treeUri: Uri, onResult: (Int) -> Unit) {
        importFacade.importFolderAsync(treeUri, onResult)
    }

    fun getSongsByPlaylist(playlist: Playlist): List<Song> {
        return playlistFacade.getSongsByPlaylist(playlist)
    }

    fun createPlaylist(name: String): Playlist? {
        return playlistFacade.createPlaylist(name)
    }

    fun deletePlaylist(playlistId: Int) {
        playlistFacade.deletePlaylist(playlistId)
    }

    fun renamePlaylist(playlistId: Int, newName: String): Boolean {
        return playlistFacade.renamePlaylist(playlistId, newName)
    }

    fun addSongToPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistFacade.addSongToPlaylist(playlistId, songId)
    }

    fun removeSongFromPlaylist(playlistId: Int, songId: Int) {
        playlistFacade.removeSongFromPlaylist(playlistId, songId)
    }

    /** 调整歌单内歌曲顺序并持久化 */
    fun reorderPlaylist(playlistId: Int, orderedSongIds: List<Int>) {
        playlistFacade.reorderPlaylist(playlistId, orderedSongIds)
    }

    fun isSongInPlaylist(playlistId: Int, songId: Int): Boolean {
        return playlistFacade.isSongInPlaylist(playlistId, songId)
    }

    suspend fun deleteSong(songId: Int): DeleteSongResult {
        return songDeletionFacade.deleteSong(songId)
    }

    fun detachSongFromGroup(song: Song): Boolean {
        return versionFacade.detachSongFromGroup(song)
    }

    fun reassignSongToGroup(song: Song, targetSong: Song): Boolean {
        return versionFacade.reassignSongToGroup(song, targetSong)
    }

    fun resetSongGroupKey(song: Song): Boolean {
        return versionFacade.resetSongGroupKey(song)
    }

    fun playRandomQueue(): Boolean {
        return randomQueueFacade.playRandomQueue()
    }

    /** 当前生效的情境时段（供 UI 归因 toast；与 facade 判定同源） */
    internal fun currentMoodSlot(): MoodSlotState? {
        return randomQueueFacade.currentMoodSlot()
    }

    fun startInfinitePlay(): Boolean {
        return randomQueueFacade.startInfinitePlay()
    }

    fun stopInfinitePlay() {
        randomQueueFacade.stopInfinitePlay()
    }

    /**
     * 开始定时关闭。
     *
     * @param durationMinutes 倒计时分钟数
     * @param playLastSong    到点后是否播完当前歌曲再暂停
     */
    fun startSleepTimer(durationMinutes: Int, playLastSong: Boolean) {
        sleepTimerCoordinator.start(durationMinutes, playLastSong)
    }

    /** 取消定时关闭 */
    fun cancelSleepTimer() {
        sleepTimerCoordinator.cancel()
    }

    fun setGlobalUniformRandomEnabled(enabled: Boolean) {
        playerSettingsRepository.setGlobalUniformRandomEnabledBlocking(enabled)
        _uiState.update { it.copy(globalUniformRandomEnabled = enabled) }
    }

    fun setDailyListeningGoalMinutes(minutes: Int) {
        playerSettingsRepository.setDailyListeningGoalMinutesBlocking(minutes)
        _uiState.update { it.copy(dailyListeningGoalMinutes = minutes) }
    }

    fun initializeBluetoothPlayback() {
        playerSettingsRepository.setBluetoothPlaybackMonitoringEnabledBlocking(true)
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = true) }
        bluetoothGraph.initialize()
    }

    fun releaseBluetoothPlayback() {
        playerSettingsRepository.setBluetoothPlaybackMonitoringEnabledBlocking(false)
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = false) }
        bluetoothGraph.release()
    }

    fun setPlaybackNotificationEnabled(enabled: Boolean) {
        playerSettingsRepository.setPlaybackNotificationEnabledBlocking(enabled)
        _uiState.update { it.copy(playbackNotificationEnabled = enabled) }
    }

    fun onCleared() {
        lifecycleFacade.onCleared()
    }

    private fun loadInitialData(afterInitialSnapshot: () -> Unit) {
        scope.launch {
            try {
                libraryFacade.loadInitialData(afterInitialSnapshot)
            } catch (e: Exception) {
                AppLog.error(TAG, "loadInitialData failed", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun savePlaybackStateAsync() {
        persistenceGraph.savePlaybackStateAsync()
    }

    private fun savePlaybackState(positionMs: Long = playbackBridgeFacade.currentPlaybackPositionMs()) {
        persistenceGraph.savePlaybackState(positionMs)
    }

    private fun clearPlaybackState() {
        persistenceGraph.clearPlaybackState()
    }

    private fun restorePlaybackState() {
        persistenceGraph.restorePlaybackState()
    }
}

/** 启动时一次性读取的用户设置快照 */
private data class StartupSettings(
    val globalUniformRandomEnabled: Boolean,
    val bluetoothPlaybackMonitoringEnabled: Boolean,
    val playbackNotificationEnabled: Boolean,
    val dailyListeningGoalMinutes: Int,
)

/**
 * 主 UiState 与高频窄流（positionMs / sleepTimerRemainingMs）的同步器。
 *
 * 设计要点（2026-09-03 回归修复，M-8 首版引入）：
 * - [MutableStateFlow.update] 的变换 lambda 必须是纯函数（CAS 竞争下会重试），
 *   所以 lambda 只做变换，窄流同步移到 update 之外；
 * - 窄流同步的对比基线是「update 前的 uiState 旧值」，绝不是窄流当前值：
 *   播放中 ticker 每 500ms 直写窄流，而 uiState 的对应字段只在离散事件时更新
 *   （其余 update 刻意 copy 保留旧值）。若拿 uiState 新值与窄流值对比，二者因
 *   节拍不同必然不等，每次离散更新都会把陈旧位置/归零的倒计时刷回窄流——
 *   表现为进度条每 ~5s 回跳抽搐、倒计时闪 00:00。
 *
 * 已知边界：M-6 后倒计时 tick 只写窄流，uiState.sleepTimerRemainingMs 与窄流
 * 可能长期不同步（uiState 恒 0）。因此「写 0 到 uiState」的取消路径无法被
 * [update] 的 uiState 对比感知，需调用方显式调用 [resetSleepTimerNarrowFlow]。
 */
internal class NarrowFlowSync(
    private val uiState: MutableStateFlow<PlayerUiState>,
    private val positionMs: MutableStateFlow<Long>,
    private val sleepTimerRemainingMs: MutableStateFlow<Long>,
) {
    fun update(transform: (PlayerUiState) -> PlayerUiState) {
        val previous = uiState.value
        var next: PlayerUiState = previous
        uiState.update { current ->
            next = transform(current)
            next
        }
        if (next.currentPositionMs != previous.currentPositionMs) {
            positionMs.value = next.currentPositionMs
        }
        if (next.sleepTimerRemainingMs != previous.sleepTimerRemainingMs) {
            sleepTimerRemainingMs.value = next.sleepTimerRemainingMs
        }
    }

    /** 显式复位倒计时窄流：cancel / fire 等把 uiState 归零的离散事件必须调用 */
    fun resetSleepTimerNarrowFlow() {
        sleepTimerRemainingMs.value = 0L
    }
}
