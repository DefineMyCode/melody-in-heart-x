package cn.com.dcsgo.mihx.feature.player

import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.CoroutineDispatchers
import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.core.model.WordPreferenceKind
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory
import cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizer
import cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicy
import cn.com.dcsgo.mihx.domain.playback.PlaybackDurationMonitorFactory
import cn.com.dcsgo.mihx.domain.model.DeleteSongResult
import cn.com.dcsgo.mihx.domain.model.DuplicateSongGroup
import cn.com.dcsgo.mihx.domain.model.FileCheckMode
import cn.com.dcsgo.mihx.domain.model.LocalFileValidationResult
import cn.com.dcsgo.mihx.domain.model.SongSortMode
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    private val wordPreferenceRepository: cn.com.dcsgo.mihx.domain.repository.WordPreferenceRepository,
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
            wordPreferenceRepository = wordPreferenceRepository,
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
    private val wordPreferenceRepository: cn.com.dcsgo.mihx.domain.repository.WordPreferenceRepository,
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

    // ── 本地音乐排序（持久化设置流） ──
    // M-5（评审 2026-10-15）：current*() 同步快照移除，直接暴露设置流。

    val songSortMode: Flow<SongSortMode> = playerSettingsRepository.songSortMode
    val songSortAscending: Flow<Boolean> = playerSettingsRepository.songSortAscending

    fun setSongSortMode(mode: SongSortMode) {
        scope.launch { playerSettingsRepository.setSongSortMode(mode) }
    }

    fun setSongSortAscending(ascending: Boolean) {
        scope.launch { playerSettingsRepository.setSongSortAscending(ascending) }
    }

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
     * 秒切歌曲列表（M-5，评审 2026-10-15）：AppNavHost 此前在组合期同步查库
     * （getQuickSkipSongs → runBlocking(IO)），每次重组两次。改为 StateFlow：
     * 启动、增删、曲库变更时后台刷新，UI collectAsState 消费。
     */
    private val _quickSkipSongs = MutableStateFlow<List<Song>>(emptyList())
    val quickSkipSongs: StateFlow<List<Song>> = _quickSkipSongs.asStateFlow()

    private suspend fun refreshQuickSkipSongs() {
        runCatching { _quickSkipSongs.value = quickSkipFacade.getQuickSkipSongs() }
            .onFailure { AppLog.warning(TAG, "refreshQuickSkipSongs failed: ${it.message}") }
    }

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
        // M-5：曲库变更（导入/删除/校验清理）时同步刷新秒切列表 StateFlow，
        // 避免被删歌曲在秒切页残留（旧实现在组合期重查库，天然是新的）
        onSongsChanged = { scope.launch { refreshQuickSkipSongs() } },
        loadPlayStatsForSort = {
            playStatsRepository.getPlayCountsForSort() to playStatsRepository.getLastPlayedAtForSort()
        },
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
                AppLog.debug(
                    TAG,
                    "syncPlayerQueue: triggered by snapshot sync (queue tail, remaining=${playbackBridgeFacade.remainingMediaItems()})",
                )
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
        trackedSongId = { trackedSongId },
        currentPlayedMs = playDurationTracker::currentPlayedMs,
        remainingMediaItems = { playbackBridgeFacade.remainingMediaItems() },
        refillInfinitePlayQueue = { startedSongId, advanceAfterWrap ->
            playbackBridgeFacade.refillInfinitePlayQueue(startedSongId, advanceAfterWrap)
        },
        refillExplorePlayQueue = { startedSongId ->
            scope.launch { randomQueueFacade.refillExplorePlayQueue(startedSongId) }
        },
        onExploreSongSettled = ::handleExploreSongSettled,
        syncPlayerQueue = { queue -> playbackBridgeFacade.syncPlayerQueue(queue) },
        log = { message -> AppLog.debug(TAG, message) },
        playOrderBuilder = playOrderBuilder,
        onSleepTimerSongEnded = { scope.launch { sleepTimerCoordinator.onSongEnded() } },
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
    // M-2（评审 2026-10-15）修复：改为持续订阅仓库流（启动即拉取当前值，之后保存/删除配置、
    // 新分析/用户校准情绪都会即时重发），不再依赖启动时一次性快照 + 手动刷新。
    // 时段配置来自 DataStore 流；情绪词条来自 Room 表失效流（映射在 Default 线程计算）。
    @Volatile
    private var moodSlotConfigsCache: List<cn.com.dcsgo.mihx.core.model.TimeSlotConfig> = emptyList()
    @Volatile
    private var moodEmotionRatiosCache: Map<Int, Map<String, Float>> = emptyMap()
    @Volatile
    private var wordPreferenceCache: Map<String, WordPreference> = emptyMap()

    private fun observeMoodSlotCaches() {
        scope.launch {
            timeSlotConfigRepository.observeConfigs()
                .catch { AppLog.warning(TAG, "observe mood slot configs failed: ${it.message}") }
                .collect { moodSlotConfigsCache = it }
        }
        scope.launch {
            songEmotionRepository.observeAll()
                .map { emotions ->
                    emotions.mapValues { (_, emotion) ->
                        cn.com.dcsgo.mihx.core.model.emotionMatchRatiosOf(emotion)
                    }
                }
                .flowOn(dispatchers.default)
                .catch { AppLog.warning(TAG, "observe mood emotion tags failed: ${it.message}") }
                .collect { moodEmotionRatiosCache = it }
        }
        // 探索播放（2026-10-20）：情绪词条收听偏好（计票制）持续订阅
        scope.launch {
            wordPreferenceRepository.observeAll()
                .catch { AppLog.warning(TAG, "observe word preferences failed: ${it.message}") }
                .collect { wordPreferenceCache = it }
        }
        // 探索播放（2026-10-20）：增强模式观察——置 isExploreModeActive，模式切离探索时退出探索会话
        scope.launch {
            playerSettingsRepository.randomEnhanceMode
                .catch { AppLog.warning(TAG, "observe random enhance mode failed: ${it.message}") }
                .collect { mode ->
                    val explore = mode == RandomEnhanceMode.EXPLORE
                    val changed = _uiState.value.isExploreModeActive != explore
                    _uiState.update { current ->
                        if (current.isExploreModeActive == explore) current
                        else current.copy(isExploreModeActive = explore)
                    }
                    if (changed) {
                        AppLog.info(TAG, "randomEnhanceMode=$mode: isExploreModeActive=$explore")
                    }
                    if (!explore && _uiState.value.isExplorePlay) {
                        randomQueueFacade.stopExplorePlay()
                        AppLog.info(TAG, "randomEnhanceMode=$mode: exited explore session")
                    }
                }
        }
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
        moodSlotEnabled = { playerSettingsRepository.randomEnhanceMode.first() == RandomEnhanceMode.TIME_SLOT },
        moodEmotionTagsOf = { song -> moodEmotionRatiosCache[song.id]?.keys?.toList().orEmpty() },
        // 时段匹配按占比卡门槛（见 MoodSlotPolicy.MATCH_MIN_RATIO）
        moodEmotionTagRatiosOf = { song -> moodEmotionRatiosCache[song.id].orEmpty() },
        exploreModeEnabled = { playerSettingsRepository.randomEnhanceMode.first() == RandomEnhanceMode.EXPLORE },
        wordPreferenceOf = { word ->
            wordPreferenceCache[word] ?: WordPreference(word = word, likeVotes = 0, dislikeVotes = 0)
        },
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
                // M-5：refill 内部要查播放计数（suspend），边界处放后台执行；
                // 补队列是追加型维护操作，与其后 restorePlayMode 的先后不敏感。
                scope.launch { randomQueueFacade.refillInfinitePlayQueue(startedSongId, advanceAfterWrap) }
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
        // M-5（评审 2026-10-15）：设置读取改为挂起 first()（原 4 次 runBlocking(IO) 已随
        // domain 接口 suspend 化消失），仍在 IO 上下文执行，不阻塞主线程首帧。
        scope.launch {
            val startupSettings = withContext(dispatchers.io) {
                StartupSettings(
                    globalUniformRandomEnabled = playerSettingsRepository.globalUniformRandomEnabled.first(),
                    bluetoothPlaybackMonitoringEnabled = playerSettingsRepository.bluetoothPlaybackMonitoringEnabled.first(),
                    playbackNotificationEnabled = playerSettingsRepository.playbackNotificationEnabled.first(),
                    dailyListeningGoalMinutes = playerSettingsRepository.dailyListeningGoalMinutes.first(),
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
            // 情境化随心播放：持续订阅时段配置与情绪词条流（M-2，低频、不阻塞启动）
            observeMoodSlotCaches()
            // 秒切列表首次加载（M-5：组合期改为消费 StateFlow，不再同步查库）
            refreshQuickSkipSongs()
            if (startupSettings.bluetoothPlaybackMonitoringEnabled) {
                bluetoothGraph.initialize()
            }
        }
    }

    fun addToQuickSkipSongs(songId: Int) {
        scope.launch {
            quickSkipFacade.addToQuickSkipSongs(songId)
            refreshQuickSkipSongs()
        }
    }

    fun removeFromQuickSkipSongs(songId: Int) {
        scope.launch {
            quickSkipFacade.removeFromQuickSkipSongs(songId)
            refreshQuickSkipSongs()
        }
    }

    /** 依据最近一次刷新的秒切列表判断（M-5：不再同步查库） */
    fun isInQuickSkipSongs(songId: Int): Boolean {
        return _quickSkipSongs.value.any { it.id == songId }
    }

    fun syncQuickSkipSongsToPlaylist() {
        scope.launch {
            quickSkipFacade.syncQuickSkipSongsToPlaylist()
        }
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
    fun validateLocalFiles(mode: FileCheckMode = FileCheckMode.QUICK) {
        if (_isValidating.value) return
        _isValidating.value = true
        scope.launch {
            try {
                val result = withContext(dispatchers.io) {
                    songRepository.validateAndCleanupLocalFiles(mode)
                }
                _validationResult.value = result
                AppLog.info(
                    TAG,
                    "validateLocalFiles($mode) done: ${result.missingCount} missing, " +
                        "${result.metadataUpdatedCount} metadata updated",
                )
            } catch (e: Exception) {
                AppLog.error(TAG, "validateLocalFiles($mode) failed", e)
            } finally {
                _isValidating.value = false
            }
        }
    }

    /** 用户确认校验结果后清除，入口徽标消失 */
    fun acknowledgeValidationResult() {
        _validationResult.value = null
    }

    // ── 重复文件去重（文件校验的一部分） ──
    private val _duplicateGroups = MutableStateFlow<List<DuplicateSongGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateSongGroup>> = _duplicateGroups.asStateFlow()

    private val _isScanningDuplicates = MutableStateFlow(false)
    val isScanningDuplicates: StateFlow<Boolean> = _isScanningDuplicates.asStateFlow()

    private val _hasScannedDuplicates = MutableStateFlow(false)
    val hasScannedDuplicates: StateFlow<Boolean> = _hasScannedDuplicates.asStateFlow()

    /** 在后台扫描曲库重复（真实路径相同）。完成后存入 [duplicateGroups]。 */
    fun scanDuplicateSongs() {
        if (_isScanningDuplicates.value) return
        _isScanningDuplicates.value = true
        scope.launch {
            try {
                val groups = withContext(dispatchers.io) {
                    songRepository.scanDuplicateSongGroups()
                }
                _duplicateGroups.value = groups
                _hasScannedDuplicates.value = true
                AppLog.info(TAG, "scanDuplicateSongs: found ${groups.size} duplicate groups")
            } catch (e: Exception) {
                AppLog.error(TAG, "scanDuplicateSongs failed", e)
            } finally {
                _isScanningDuplicates.value = false
            }
        }
    }

    /** 清理全部重复组，返回被移除的歌曲 id 数。 */
    fun deduplicateAll() {
        val groups = _duplicateGroups.value
        if (groups.isEmpty()) return
        scope.launch {
            try {
                val removed = withContext(dispatchers.io) {
                    songRepository.deduplicateSongs(groups)
                }
                AppLog.info(TAG, "deduplicateAll: removed ${removed.size} songs")
                _duplicateGroups.value = emptyList()
                _hasScannedDuplicates.value = false
                // 清空扫描结果与校验徽标
                _validationResult.value = null
            } catch (e: Exception) {
                AppLog.error(TAG, "deduplicateAll failed", e)
            }
        }
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

    /**
     * 随心播放（M-5）：原同步返回 Boolean 会在主线程 runBlocking 查播放计数。
     * 改为后台执行、结果经回调送回（scope = viewModelScope = Main，回调里可直接 toast）。
     *
     * @param onResult started=是否已生成队列开始播放；moodSlotName=命中的情境时段名（归因 toast）
     */
    fun playRandomQueue(onResult: (started: Boolean, moodSlotName: String?) -> Unit = { _, _ -> }) {
        scope.launch {
            val started = randomQueueFacade.playRandomQueue()
            val moodSlotName = randomQueueFacade.currentMoodSlot()?.slotName
            onResult(started, moodSlotName)
        }
    }

    /** 开启无限随机播放（M-5）：同 [playRandomQueue]，异步 + 回调 */
    fun startInfinitePlay(onResult: (started: Boolean, moodSlotName: String?) -> Unit = { _, _ -> }) {
        scope.launch {
            val started = randomQueueFacade.startInfinitePlay()
            val moodSlotName = randomQueueFacade.currentMoodSlot()?.slotName
            onResult(started, moodSlotName)
        }
    }

    fun stopInfinitePlay() {
        randomQueueFacade.stopInfinitePlay()
    }

    /**
     * 探索播放收听结果学习（2026-10-20）：一首歌结束/切歌时结算其收听时长，
     * 按「<30s 或 <1/4 总时长 → 快速跳过 → 全部情绪词条投不喜欢票，否则投喜欢票」，
     * 计票制入库（[WordPreferenceRepository.recordSongOutcome]）。
     *
     * 2026-10-25：**票重按词条在整首里占的比例给** —— 跳过一首 80% emo 的歌，
     * 「emo」扣得多、顺带蹭到的次要词扣得少；原来每个词一刀切 1 票，主次不分。
     */
    private fun handleExploreSongSettled(songId: Int, playedMs: Long) {
        val song = _uiState.value.songs.firstOrNull { it.id == songId }
        val ratios = moodEmotionRatiosCache[songId].orEmpty()
        val skipped = ExplorePlaybackPolicy.isSkipped(playedMs, song?.durationMs ?: 0L)
        if (ratios.isEmpty()) {
            AppLog.debug(
                TAG,
                "explore settle: song=$songId played=${playedMs}ms skipped=$skipped, no emotion words to learn"
            )
            return
        }
        // 词条占比 -> 票重（整数票, 比例 ×100）
        val votes = ratios.mapValues { (_, ratio) -> ExplorePlaybackPolicy.voteWeightOf(ratio) }
            .filterValues { it > 0 }
        if (votes.isEmpty()) {
            AppLog.debug(
                TAG,
                "explore settle: song=$songId has no word with meaningful share, nothing to learn"
            )
            return
        }
        scope.launch {
            try {
                wordPreferenceRepository.recordSongOutcome(votes, liked = !skipped)
                // 展示当前全局喜欢/不喜欢/中立词条（写后重读快照，验证计票制累计与翻转）
                val prefs = wordPreferenceRepository.getAll()
                val likeWords = prefs.filterValues { it.kind == WordPreferenceKind.LIKE }.keys.sorted()
                val dislikeWords = prefs.filterValues { it.kind == WordPreferenceKind.DISLIKE }.keys.sorted()
                val neutralWords = prefs.filterValues { it.kind == WordPreferenceKind.NEUTRAL }.keys.sorted()
                AppLog.info(
                    TAG,
                    "explore learn: song=$songId played=${playedMs}ms skipped=$skipped " +
                        "votes=${votes.entries.joinToString(",") { "${it.key}x${it.value}" }}"
                )
                AppLog.info(
                    TAG,
                    "explore preference snapshot: like=[${likeWords.joinToString(",")}] " +
                        "dislike=[${dislikeWords.joinToString(",")}] neutral=[${neutralWords.joinToString(",")}]"
                )
            } catch (e: Exception) {
                AppLog.error(TAG, "explore learn failed: ${e.message}", e)
            }
        }
    }

    /**
     * 开始定时关闭。
     *
     * @param durationMinutes 倒计时分钟数
     * @param playLastSong    到点后是否播完当前歌曲再暂停
     */
    fun startSleepTimer(durationMinutes: Int, playLastSong: Boolean) {
        scope.launch { sleepTimerCoordinator.start(durationMinutes, playLastSong) }
    }

    /** 取消定时关闭 */
    fun cancelSleepTimer() {
        scope.launch { sleepTimerCoordinator.cancel() }
    }

    fun setGlobalUniformRandomEnabled(enabled: Boolean) {
        scope.launch { playerSettingsRepository.setGlobalUniformRandomEnabled(enabled) }
        _uiState.update { it.copy(globalUniformRandomEnabled = enabled) }
    }

    fun setDailyListeningGoalMinutes(minutes: Int) {
        scope.launch { playerSettingsRepository.setDailyListeningGoalMinutes(minutes) }
        _uiState.update { it.copy(dailyListeningGoalMinutes = minutes) }
    }

    fun initializeBluetoothPlayback() {
        scope.launch { playerSettingsRepository.setBluetoothPlaybackMonitoringEnabled(true) }
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = true) }
        bluetoothGraph.initialize()
    }

    fun releaseBluetoothPlayback() {
        scope.launch { playerSettingsRepository.setBluetoothPlaybackMonitoringEnabled(false) }
        _uiState.update { it.copy(bluetoothPlaybackMonitoringEnabled = false) }
        bluetoothGraph.release()
    }

    fun setPlaybackNotificationEnabled(enabled: Boolean) {
        scope.launch { playerSettingsRepository.setPlaybackNotificationEnabled(enabled) }
        _uiState.update { it.copy(playbackNotificationEnabled = enabled) }
    }

    fun onCleared() {
        lifecycleFacade.onCleared()
    }

    private fun loadInitialData(afterInitialSnapshot: () -> Unit) {
        scope.launch {
            // 恢复触发只执行一次：曲库加载成功（库内 afterInitialSnapshot）或失败（catch 兜底）
            // 都必须放行，否则 Room 恢复抛异常时快照永不读、播放队列/进度静默丢失（2026-10-06）。
            var restoreTriggered = false
            val triggerRestore = {
                if (!restoreTriggered) {
                    restoreTriggered = true
                    afterInitialSnapshot()
                }
            }
            try {
                libraryFacade.loadInitialData(triggerRestore)
            } catch (e: Exception) {
                AppLog.error(TAG, "loadInitialData failed, still attempting restore", e)
                _uiState.update { it.copy(isLoading = false) }
                triggerRestore()
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
